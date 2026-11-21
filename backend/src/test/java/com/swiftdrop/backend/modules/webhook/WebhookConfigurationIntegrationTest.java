package com.swiftdrop.backend.modules.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.backend.modules.auth.entity.AccountStatus;
import com.swiftdrop.backend.modules.auth.entity.Role;
import com.swiftdrop.backend.modules.auth.entity.User;
import com.swiftdrop.backend.modules.auth.repository.UserRepository;
import com.swiftdrop.backend.modules.auth.security.JwtTokenProvider;
import com.swiftdrop.backend.modules.messaging.event.DomainEventMessage;
import com.swiftdrop.backend.modules.messaging.event.ParcelEvents;
import com.swiftdrop.backend.modules.messaging.event.ParcelStatusChangedPayload;
import com.swiftdrop.backend.modules.merchant.entity.Merchant;
import com.swiftdrop.backend.modules.merchant.entity.MerchantRegistrationStatus;
import com.swiftdrop.backend.modules.merchant.repository.MerchantRepository;
import com.swiftdrop.backend.modules.parcel.repository.ParcelRepository;
import com.swiftdrop.backend.modules.tracking.domain.ParcelStatus;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpClient;
import com.swiftdrop.backend.modules.webhook.client.WebhookHttpResult;
import com.swiftdrop.backend.modules.webhook.controller.WebhookConfigurationController;
import com.swiftdrop.backend.modules.webhook.dto.WebhookConfigurationResponse;
import com.swiftdrop.backend.modules.webhook.endpoint.JdbcMerchantWebhookDirectory;
import com.swiftdrop.backend.modules.webhook.repository.MerchantWebhookConfigurationRepository;
import com.swiftdrop.backend.modules.webhook.repository.WebhookDeliveryRepository;
import com.swiftdrop.backend.modules.webhook.service.AttemptOutcome;
import com.swiftdrop.backend.modules.webhook.service.WebhookConfigurationService;
import com.swiftdrop.backend.modules.webhook.service.WebhookDispatchService;
import com.swiftdrop.backend.modules.webhook.signing.WebhookSigner;
import com.swiftdrop.backend.modules.webhook.validation.WebhookAddressResolver;
import com.swiftdrop.backend.support.TestParcels;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** Real JWT, security chain, HTTP controller, persistence and directory; no external services. */
@SpringBootTest(properties = {"app.messaging.outbox.publisher-enabled=false", "spring.rabbitmq.dynamic=false"})
@AutoConfigureMockMvc
@Transactional
class WebhookConfigurationIntegrationTest {
    private static final String PATH = WebhookConfigurationController.PATH;
    private static final String URL = "https://hooks.shop.com/events";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @Autowired MerchantWebhookConfigurationRepository configurations;
    @Autowired MerchantRepository merchantProfiles;
    @Autowired WebhookConfigurationService service;
    @Autowired JdbcMerchantWebhookDirectory directory;
    @Autowired ParcelRepository parcels;
    @Autowired WebhookDispatchService dispatch;
    @Autowired WebhookDeliveryRepository deliveries;
    @Autowired WebhookSigner signer;
    @Autowired JsonMapper mapper;
    @Autowired EntityManager entityManager;
    @MockitoBean WebhookAddressResolver resolver;
    @MockitoBean WebhookHttpClient http;

    private User merchant;
    private User other;

    @BeforeEach
    void setUp() throws Exception {
        merchant = user("owner@shop.com", Role.ROLE_MERCHANT, AccountStatus.ACTIVE);
        other = user("other@shop.com", Role.ROLE_MERCHANT, AccountStatus.ACTIVE);
        when(resolver.resolve("hooks.shop.com")).thenReturn(new InetAddress[]{InetAddress.getByName("8.8.8.8")});
        when(resolver.resolve("new.shop.com")).thenReturn(new InetAddress[]{InetAddress.getByName("8.8.4.4")});
    }

    @Test
    void getBeforeSetupIsEmptyAndDoesNotCreateAnAccount() throws Exception {
        mvc.perform(get(PATH).header("Authorization", bearer(merchant)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.secret").doesNotExist());
        assertThat(configurations.findByUserId(merchant.getId())).isEmpty();
    }

    @Test
    void firstPutCreatesSecretOnceAndUpdatesPreserveIt() throws Exception {
        String json = mvc.perform(put(PATH).header("Authorization", bearer(merchant))
                        .contentType("application/json").content("{\"url\":\"" + URL + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.configured").value(true)).andReturn().getResponse().getContentAsString();
        String secret = mapper.readTree(json).get("secret").asText();
        assertThat(secret).startsWith("whsec_");
        assertThat(Base64.getUrlDecoder().decode(secret.substring(6))).hasSize(32);

        mvc.perform(get(PATH).header("Authorization", bearer(merchant)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.secret").doesNotExist());
        mvc.perform(put(PATH).header("Authorization", bearer(merchant)).contentType("application/json")
                        .content("{\"url\":\"https://new.shop.com/events\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secret").doesNotExist());
        var stored = configurations.findByUserId(merchant.getId()).orElseThrow();
        assertThat(stored.getWebhookSecret()).isEqualTo(secret);
        assertThat(directory.findEndpoint(stored.getId()).orElseThrow().url()).isEqualTo("https://new.shop.com/events");
        assertThat(new WebhookConfigurationResponse(URL, true, secret).toString()).doesNotContain(secret);
        entityManager.flush();
        entityManager.clear();
        var profile = merchantProfiles.findByUserId(merchant.getId()).orElseThrow();
        assertThat(profile.getId()).isEqualTo(stored.getId());
        assertThat(profile.getBusinessName()).isEqualTo(merchant.getFullName());
        assertThat(profile.getRegistrationStatus()).isEqualTo(MerchantRegistrationStatus.APPROVED);
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getUpdatedAt()).isNotNull();
    }

    @Test
    void webhookUpdatePreservesExistingMerchantProfileAndLongSecret() {
        String existingSecret = "whsec_" + "x".repeat(180);
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        var profile = merchantProfiles.saveAndFlush(Merchant.builder()
                .userId(merchant.getId()).businessName("Existing Business")
                .registrationNumber("REG-TEST").registrationStatus(MerchantRegistrationStatus.APPROVED)
                .webhookUrl(URL).webhookSecret(existingSecret).createdAt(created).build());

        var result = service.put(merchant.getEmail(), "https://new.shop.com/events");
        entityManager.flush();
        entityManager.clear();

        var unchanged = merchantProfiles.findById(profile.getId()).orElseThrow();
        assertThat(result.secret()).isNull();
        assertThat(unchanged.getBusinessName()).isEqualTo("Existing Business");
        assertThat(unchanged.getRegistrationNumber()).isEqualTo("REG-TEST");
        assertThat(unchanged.getRegistrationStatus()).isEqualTo(MerchantRegistrationStatus.APPROVED);
        assertThat(unchanged.getCreatedAt()).isEqualTo(created);
        assertThat(unchanged.getWebhookSecret()).isEqualTo(existingSecret);
        assertThat(unchanged.getWebhookUrl()).isEqualTo("https://new.shop.com/events");
    }

    @ParameterizedTest
    @EnumSource(value = MerchantRegistrationStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void existingUnapprovedProfilesRemainBlocked(MerchantRegistrationStatus registrationStatus) throws Exception {
        var profile = merchantProfiles.saveAndFlush(Merchant.builder().userId(merchant.getId())
                .businessName("Existing Business").registrationStatus(registrationStatus).build());

        mvc.perform(get(PATH).header("Authorization", bearer(merchant))).andExpect(status().isForbidden());
        mvc.perform(put(PATH).header("Authorization", bearer(merchant)).contentType("application/json")
                .content("{\"url\":\"" + URL + "\"}")).andExpect(status().isForbidden());

        entityManager.clear();
        var unchanged = merchantProfiles.findById(profile.getId()).orElseThrow();
        assertThat(unchanged.getRegistrationStatus()).isEqualTo(registrationStatus);
        assertThat(unchanged.getWebhookUrl()).isNull();
        assertThat(unchanged.getWebhookSecret()).isNull();
    }

    @Test
    void callerSuppliedIdentityCannotReadOrOverwriteAnotherMerchant() throws Exception {
        String otherSecret = service.put(other.getEmail(), URL).secret();
        UUID otherId = configurations.findByUserId(other.getId()).orElseThrow().getId();

        mvc.perform(get(PATH).header("Authorization", bearer(merchant)).param("merchantId", otherId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(put(PATH).header("Authorization", bearer(merchant)).contentType("application/json")
                        .content("{\"url\":\"https://new.shop.com/events\",\"merchantId\":\"" + otherId
                                + "\",\"userId\":\"" + other.getId() + "\",\"secret\":\"attacker-secret\"}"))
                .andExpect(status().isOk());

        var unchanged = configurations.findByUserId(other.getId()).orElseThrow();
        assertThat(unchanged.getWebhookUrl()).isEqualTo(URL);
        assertThat(unchanged.getWebhookSecret()).isEqualTo(otherSecret);
        var own = configurations.findByUserId(merchant.getId()).orElseThrow();
        assertThat(own.getId()).isNotEqualTo(otherId);
        assertThat(own.getWebhookSecret()).isNotEqualTo("attacker-secret").isNotEqualTo(otherSecret);
    }

    @Test
    void missingOrInvalidAuthenticationIsUnauthorized() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(put(PATH).contentType("application/json").content("{\"url\":\"" + URL + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
        assertThat(configurations.findByUserId(merchant.getId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ROLE_MERCHANT", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesAreForbidden(Role role) throws Exception {
        User nonMerchant = user("role@shop.com", role, AccountStatus.ACTIVE);
        mvc.perform(get(PATH).header("Authorization", bearer(nonMerchant))).andExpect(status().isForbidden());
        mvc.perform(put(PATH).header("Authorization", bearer(nonMerchant)).contentType("application/json")
                .content("{\"url\":\"" + URL + "\"}")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void inactiveMerchantsCannotUseAnOldToken(AccountStatus accountStatus) throws Exception {
        String oldToken = bearer(merchant);
        merchant.setStatus(accountStatus);
        users.saveAndFlush(merchant);
        mvc.perform(get(PATH).header("Authorization", oldToken)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).header("Authorization", oldToken).contentType("application/json")
                .content("{\"url\":\"" + URL + "\"}")).andExpect(status().isForbidden());
    }

    @Test
    void tokenForDeletedMerchantCannotCreateConfiguration() throws Exception {
        String oldToken = bearer(merchant);
        users.delete(merchant);
        users.flush();
        mvc.perform(get(PATH).header("Authorization", oldToken)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).header("Authorization", oldToken).contentType("application/json")
                .content("{\"url\":\"" + URL + "\"}")).andExpect(status().isForbidden());
    }

    @Test
    void invalidDestinationsAndInvalidBodiesLeaveConfigurationUnchanged() throws Exception {
        String secret = service.put(merchant.getEmail(), URL).secret();
        for (String body : new String[]{"{", "null", "[]", "{}", "{\"url\":\"\"}", "{\"url\":\"file:///etc/passwd\"}",
                "{\"url\":\"http://localhost/events\"}", "{\"url\":\"" + "x".repeat(501) + "\"}"}) {
            mvc.perform(put(PATH).header("Authorization", bearer(merchant)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        var stored = configurations.findByUserId(merchant.getId()).orElseThrow();
        assertThat(stored.getWebhookUrl()).isEqualTo(URL);
        assertThat(stored.getWebhookSecret()).isEqualTo(secret);
    }

    @Test
    void existingMerchantWithoutSecretIsRepairedWithoutChangingItsId() {
        service.put(merchant.getEmail(), URL);
        var stored = configurations.findByUserId(merchant.getId()).orElseThrow();
        UUID existingId = stored.getId();
        stored.configure(URL, null);
        configurations.saveAndFlush(stored);

        var result = service.put(merchant.getEmail(), URL);
        assertThat(result.secret()).startsWith("whsec_");
        assertThat(configurations.findByUserId(merchant.getId()).orElseThrow().getId()).isEqualTo(existingId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void realDirectoryResolvesLinkedParcelAndEngineUsesConfiguredSecret() {
        String secret = service.put(merchant.getEmail(), URL).secret();
        UUID merchantId = configurations.findByUserId(merchant.getId()).orElseThrow().getId();
        var parcel = TestParcels.withId(null);
        setField(parcel, "merchantId", merchantId);
        parcel = parcels.saveAndFlush(parcel);
        entityManager.clear();
        assertThat(parcels.findById(parcel.getId()).orElseThrow().getMerchantId()).isEqualTo(merchantId);
        assertThat(directory.findMerchantIdForUser(merchant.getEmail())).contains(merchantId);
        assertThat(directory.findMerchantIdForParcel(parcel.getId())).contains(merchantId);
        assertThat(directory.findEndpoint(merchantId).orElseThrow().secret()).isEqualTo(secret);

        Instant now = Instant.now();
        var payload = new ParcelStatusChangedPayload(parcel.getId(), parcel.getTrackingNumber(), ParcelStatus.CREATED,
                ParcelStatus.PICKED_UP, null, now);
        var event = new DomainEventMessage(UUID.randomUUID(), ParcelEvents.STATUS_CHANGED, "parcel", parcel.getId(), now,
                mapper.valueToTree(payload));
        when(http.post(anyString(), anyString(), anyMap())).thenReturn(WebhookHttpResult.response(204));
        assertThat(dispatch.handleEvent(event)).isEqualTo(AttemptOutcome.DELIVERED);
        var body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).post(org.mockito.ArgumentMatchers.eq(URL), body.capture(), headers.capture());
        assertThat(signer.verify(secret, headers.getValue().get(WebhookSigner.SIGNATURE_HEADER), body.getValue(),
                Instant.now(), Duration.ofMinutes(5))).isTrue();
        assertThat(deliveries.findByEventId(event.eventId())).hasSize(1);
    }

    @Test
    void customerParcelsRemainUnlinkedAndChangedDnsIsBlocked() throws Exception {
        service.put(merchant.getEmail(), URL);
        UUID merchantId = configurations.findByUserId(merchant.getId()).orElseThrow().getId();
        var parcel = TestParcels.withId(null);
        parcel = parcels.saveAndFlush(parcel);
        assertThat(directory.findMerchantIdForParcel(parcel.getId())).isEmpty();
        when(resolver.resolve("hooks.shop.com")).thenReturn(new InetAddress[]{InetAddress.getByName("10.0.0.1")});
        assertThat(directory.findEndpoint(merchantId)).isEmpty();
    }

    private User user(String email, Role role, AccountStatus accountStatus) {
        return users.saveAndFlush(User.builder().email(email).fullName("Test User").role(role)
                .status(accountStatus).emailVerified(true).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateAccessToken(user);
    }
}
