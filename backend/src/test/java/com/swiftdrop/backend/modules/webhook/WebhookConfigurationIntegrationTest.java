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
    @Autowired MerchantRepository configurations;
    @Autowired MerchantRepository merchantProfiles;
    @Autowired WebhookConfigurationService service;
    @Autowired JdbcMerchantWebhookDirectory directory;
    @Autowired ParcelRepository parcels;
    @Autowired WebhookDispatchService dispatch;
    @Autowired WebhookDeliveryRepository deliveries;
    @Autowired WebhookSigner signer;
    @Autowired JsonMapper mapper;
    @Autowired EntityManager entityManager;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagers;
    @MockitoBean WebhookAddressResolver resolver;
    @MockitoBean WebhookHttpClient http;
    @MockitoBean com.swiftdrop.backend.modules.auth.service.EmailService emailService;
    @Autowired com.swiftdrop.backend.modules.auth.service.AuthService auth;
    @Autowired com.swiftdrop.backend.modules.auth.repository.EmailVerificationTokenRepository verificationCodes;
    @Autowired com.swiftdrop.backend.modules.merchant.service.MerchantAdminService approvals;

    private User merchant;
    private User other;

    @BeforeEach
    void setUp() throws Exception {
        merchant = user("owner@shop.com", Role.ROLE_MERCHANT, AccountStatus.ACTIVE);
        other = user("other@shop.com", Role.ROLE_MERCHANT, AccountStatus.ACTIVE);
        approvedProfile(merchant);
        approvedProfile(other);
        when(resolver.resolve("hooks.shop.com")).thenReturn(new InetAddress[]{InetAddress.getByName("8.8.8.8")});
        when(resolver.resolve("new.shop.com")).thenReturn(new InetAddress[]{InetAddress.getByName("8.8.4.4")});
        when(resolver.resolve("127.0.0.1")).thenReturn(new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        when(resolver.resolve("10.0.0.1")).thenReturn(new InetAddress[]{InetAddress.getByName("10.0.0.1")});
    }

    @Test
    void getBeforeSetupIsEmptyAndDoesNotCreateAnAccount() throws Exception {
        mvc.perform(get(PATH).header("Authorization", bearer(merchant)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.secret").doesNotExist());
        assertThat(configurations.findByUserId(merchant.getId()).orElseThrow().getWebhookUrl()).isNull();
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
        merchantProfiles.delete(merchantProfiles.findByUserId(merchant.getId()).orElseThrow());
        merchantProfiles.flush();
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
        merchantProfiles.delete(merchantProfiles.findByUserId(merchant.getId()).orElseThrow());
        merchantProfiles.flush();
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
        assertThat(configurations.findByUserId(merchant.getId()).orElseThrow().getWebhookUrl()).isNull();
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
        stored.setWebhookUrl(URL);
        stored.setWebhookSecret(null);
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

    @Test
    void missingProfileCannotBeCreatedOrApprovedByConfiguration() throws Exception {
        var legacy = user("legacy@shop.com", Role.ROLE_MERCHANT, AccountStatus.ACTIVE);
        for (String path : new String[]{PATH, WebhookConfigurationController.LEGACY_PATH}) {
            mvc.perform(get(path).header("Authorization", bearer(legacy))).andExpect(status().isForbidden());
            mvc.perform(put(path).header("Authorization", bearer(legacy)).contentType("application/json")
                    .content("{\"url\":\"" + URL + "\"}")).andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path + "/rotate-secret")
                    .header("Authorization", bearer(legacy))).andExpect(status().isForbidden());
        }
        assertThat(configurations.findByUserId(legacy.getId())).isEmpty();
    }

    @Test
    void singularAndPluralRoutesShareConfigurationAndIgnoreCallerSecrets() throws Exception {
        String json = mvc.perform(put(WebhookConfigurationController.LEGACY_PATH)
                .header("Authorization", bearer(merchant)).contentType("application/json")
                .content("{\"webhookUrl\":\"" + URL + "\",\"webhookSecret\":\"caller-secret\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        String secret = mapper.readTree(json).get("secret").asText();
        assertThat(secret).startsWith("whsec_").isNotEqualTo("caller-secret");
        mvc.perform(get(PATH).header("Authorization", bearer(merchant)))
                .andExpect(jsonPath("$.url").value(URL)).andExpect(jsonPath("$.secret").doesNotExist());
        mvc.perform(put(PATH).header("Authorization", bearer(merchant)).contentType("application/json")
                .content("{\"url\":\"https://new.shop.com/events\",\"secret\":\"replacement\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secret").doesNotExist());
        mvc.perform(get(WebhookConfigurationController.LEGACY_PATH).header("Authorization", bearer(merchant)))
                .andExpect(jsonPath("$.url").value("https://new.shop.com/events"))
                .andExpect(jsonPath("$.secret").doesNotExist());
        mvc.perform(get("/api/v1/merchant/profile").header("Authorization", bearer(merchant)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.webhookSecret").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());
        assertThat(configurations.findByUserId(merchant.getId()).orElseThrow().getWebhookSecret()).isEqualTo(secret);
    }

    @Test
    void rotationIsExplicitOwnedAndReturnsOnlyTheNewSecret() throws Exception {
        String original = service.put(merchant.getEmail(), URL).secret();
        String otherSecret = service.put(other.getEmail(), URL).secret();
        String json = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH + "/rotate-secret")
                .header("Authorization", bearer(merchant)).contentType("application/json")
                .content("{\"merchantId\":\"" + configurations.findByUserId(other.getId()).orElseThrow().getId() + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        String rotated = mapper.readTree(json).get("secret").asText();
        assertThat(rotated).startsWith("whsec_").isNotEqualTo(original);
        assertThat(configurations.findByUserId(other.getId()).orElseThrow().getWebhookSecret()).isEqualTo(otherSecret);
        assertThat(service.put(merchant.getEmail(), URL).secret()).isNull();
        assertThat(service.get(merchant.getEmail()).secret()).isNull();
        assertThat(configurations.findByUserId(merchant.getId()).orElseThrow().getWebhookSecret()).isEqualTo(rotated);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH + "/rotate-secret"))
                .andExpect(status().isUnauthorized());
        var customer = user("rotation-customer@shop.com", Role.ROLE_CUSTOMER, AccountStatus.ACTIVE);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH + "/rotate-secret")
                .header("Authorization", bearer(customer))).andExpect(status().isForbidden());
    }

    @Test
    void legacyPreGeneratedSecretIsPreservedUntilExplicitRecovery() throws Exception {
        var profile = configurations.findByUserId(merchant.getId()).orElseThrow();
        profile.setWebhookSecret("whsec_legacy_unrecoverable");
        configurations.saveAndFlush(profile);
        assertThat(service.put(merchant.getEmail(), URL).secret()).isNull();
        assertThat(profile.getWebhookSecret()).isEqualTo("whsec_legacy_unrecoverable");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                WebhookConfigurationController.LEGACY_PATH + "/rotate-secret")
                .header("Authorization", bearer(merchant)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secret").isNotEmpty());
        assertThat(profile.getWebhookSecret()).isNotEqualTo("whsec_legacy_unrecoverable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void signupApprovalConfigurationAndReceiverVerificationUseOneSecret() throws Exception {
        var signup = com.swiftdrop.backend.modules.auth.dto.SignUpRequest.builder()
                .email("new-merchant@shop.com").password("TestPass!123").fullName("New Merchant")
                .phone("0771234567").role(Role.ROLE_MERCHANT).businessName("New Shop")
                .registrationNumber("REG-NEW").businessAddress("1 Test Road").build();
        var registered = auth.register(signup);
        User applicant = users.findById(registered.getUser().getId()).orElseThrow();
        var profile = configurations.findByUserId(applicant.getId()).orElseThrow();
        assertThat(profile.getWebhookSecret()).isNull();
        assertThat(profile.getRegistrationStatus()).isEqualTo(MerchantRegistrationStatus.PENDING);
        mvc.perform(put(PATH).header("Authorization", bearer(applicant)).contentType("application/json")
                .content("{\"url\":\"" + URL + "\"}")).andExpect(status().isForbidden());
        String code = verificationCodes.findAll().stream().filter(token -> token.getUser().getId().equals(applicant.getId()))
                .findFirst().orElseThrow().getToken();
        assertThat(auth.verifyEmail(new com.swiftdrop.backend.modules.auth.dto.VerifyEmailRequest(signup.getEmail(), code))
                .getAccessToken()).isNull();
        approvals.approveMerchant(profile.getId(), "Approved test application", UUID.randomUUID());
        String json = mvc.perform(put(PATH).header("Authorization", bearer(applicant)).contentType("application/json")
                .content("{\"url\":\"" + URL + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secret = mapper.readTree(json).get("secret").asText();
        when(http.post(anyString(), anyString(), anyMap())).thenReturn(WebhookHttpResult.response(204));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/merchant/webhook/test")
                .header("Authorization", bearer(applicant))).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        var body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).post(org.mockito.ArgumentMatchers.eq(URL), body.capture(), headers.capture());
        assertThat(signer.verify(secret, headers.getValue().get(WebhookSigner.SIGNATURE_HEADER), body.getValue(),
                Instant.now(), Duration.ofMinutes(5))).isTrue();
        assertThat(headers.getValue()).containsKeys("X-SwiftDrop-Event-Id", "X-SwiftDrop-Delivery", "X-SwiftDrop-Attempt");
        assertThat(body.getValue()).doesNotContain(secret);
        assertThat(deliveries.findByEventId(UUID.fromString(headers.getValue().get("X-SwiftDrop-Event-Id")))).hasSize(1);
    }

    @Test
    void testDestinationsAreValidatedAndLogsAreScopedToTheEngineOwner() throws Exception {
        service.put(merchant.getEmail(), URL);
        for (String url : new String[]{"http://localhost/hook", "http://127.0.0.1/hook", "http://10.0.0.1/hook", "file:///tmp/hook"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/merchant/webhook/test")
                    .header("Authorization", bearer(merchant)).contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("webhookUrl", url))))
                    .andExpect(status().isBadRequest());
        }
        org.mockito.Mockito.verifyNoInteractions(http);
        when(http.post(anyString(), anyString(), anyMap())).thenReturn(WebhookHttpResult.response(503));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/merchant/webhook/test")
                .header("Authorization", bearer(merchant))).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        var profile = configurations.findByUserId(merchant.getId()).orElseThrow();
        var delivery = deliveries.findByMerchantId(profile.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().getFirst();
        mvc.perform(get("/api/v1/merchant/webhooks/deliveries").header("Authorization", bearer(merchant)))
                .andExpect(jsonPath("$.content[0].id").value(delivery.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("DEAD"));
        mvc.perform(get("/api/v1/merchant/webhook/logs").header("Authorization", bearer(merchant)))
                .andExpect(jsonPath("$[0].id").value(delivery.getId().toString()))
                .andExpect(jsonPath("$[0].deliveryStatus").value("FAILED"));
        mvc.perform(get("/api/v1/merchant/webhooks/deliveries/" + delivery.getId()).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/merchant/webhook/logs").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void suspensionBlocksQueuedRetriesWithoutContactingReceiver() {
        service.put(merchant.getEmail(), URL);
        var profile = configurations.findByUserId(merchant.getId()).orElseThrow();
        var delivery = com.swiftdrop.backend.modules.webhook.entity.WebhookDelivery.create(UUID.randomUUID(),
                profile.getId(), null, "TEST-PING", "webhook.test_ping", URL, "{}", 3);
        delivery.recordFailure(URL, 503, "Unavailable", Instant.now());
        delivery.scheduleRetry(Instant.now());
        delivery = deliveries.saveAndFlush(delivery);
        merchant.setStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(merchant);
        assertThat(directory.findEndpoint(profile.getId())).isEmpty();
        assertThat(dispatch.retry(delivery.getId())).isEqualTo(AttemptOutcome.DEAD);
        org.mockito.Mockito.verifyNoInteractions(http);
    }

    @Test
    void rotationAndTestingRequireAConfigurationAndRejectUnsafeSavedDestinations() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH + "/rotate-secret")
                .header("Authorization", bearer(merchant))).andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/merchant/webhook/test")
                .header("Authorization", bearer(merchant))).andExpect(status().isConflict());
        var profile = configurations.findByUserId(merchant.getId()).orElseThrow();
        profile.setWebhookUrl("http://localhost/hook");
        profile.setWebhookSecret("whsec_legacy");
        configurations.saveAndFlush(profile);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH + "/rotate-secret")
                .header("Authorization", bearer(merchant))).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/merchant/webhook/test")
                .header("Authorization", bearer(merchant))).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(http);
    }

    @Test
    void staleProfileUpdateDoesNotOverwriteAnotherTransactionsWebhookConfiguration() {
        UUID userId;
        UUID merchantId;
        try (var seed = entityManagers.createEntityManager()) {
            seed.getTransaction().begin();
            var account = User.builder().email("concurrent-" + UUID.randomUUID() + "@shop.com")
                    .fullName("Concurrent Merchant").role(Role.ROLE_MERCHANT).status(AccountStatus.ACTIVE)
                    .emailVerified(true).build();
            seed.persist(account);
            var profile = Merchant.builder().userId(account.getId()).businessName("Original Business")
                    .registrationStatus(MerchantRegistrationStatus.APPROVED).build();
            seed.persist(profile);
            seed.getTransaction().commit();
            userId = account.getId();
            merchantId = profile.getId();
        }
        try (var stale = entityManagers.createEntityManager(); var writer = entityManagers.createEntityManager()) {
            stale.getTransaction().begin();
            var oldSnapshot = stale.find(Merchant.class, merchantId);
            writer.getTransaction().begin();
            var configuration = writer.find(Merchant.class, merchantId);
            configuration.setWebhookUrl(URL);
            configuration.setWebhookSecret("whsec_concurrency_test");
            writer.getTransaction().commit();
            oldSnapshot.setBusinessName("Updated Business");
            stale.getTransaction().commit();
            writer.clear();
            var finalProfile = writer.find(Merchant.class, merchantId);
            assertThat(finalProfile.getBusinessName()).isEqualTo("Updated Business");
            assertThat(finalProfile.getWebhookUrl()).isEqualTo(URL);
            assertThat(finalProfile.getWebhookSecret()).isEqualTo("whsec_concurrency_test");
            assertThat(entityManagers.getMetamodel().getEntities().stream()
                    .filter(type -> type.getJavaType().getAnnotation(jakarta.persistence.Table.class) != null)
                    .filter(type -> "merchants".equals(type.getJavaType().getAnnotation(jakarta.persistence.Table.class).name())))
                    .hasSize(1);
        } finally {
            // Only these isolated H2 fixtures were committed; remove them even if an assertion fails.
            try (var cleanup = entityManagers.createEntityManager()) {
                cleanup.getTransaction().begin();
                cleanup.remove(cleanup.find(Merchant.class, merchantId));
                cleanup.remove(cleanup.find(User.class, userId));
                cleanup.getTransaction().commit();
            }
        }
    }

    private Merchant approvedProfile(User user) {
        return merchantProfiles.saveAndFlush(Merchant.builder().userId(user.getId())
                .businessName(user.getFullName()).registrationStatus(MerchantRegistrationStatus.APPROVED).build());
    }

    private User user(String email, Role role, AccountStatus accountStatus) {
        return users.saveAndFlush(User.builder().email(email).fullName("Test User").role(role)
                .status(accountStatus).emailVerified(true).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateAccessToken(user);
    }
}
