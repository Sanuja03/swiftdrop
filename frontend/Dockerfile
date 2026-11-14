
# Build the React application
FROM node:22-alpine AS build

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci

# Copy source code and build
COPY . .
RUN npm run build

# Serve the production build
FROM nginx:alpine

# Copy the production build into Nginx
COPY --from=build /app/dist /usr/share/nginx/html

# SPA routing plus /api and /ws forwarding to the backend
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
