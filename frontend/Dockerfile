# --- Etapa de build ---
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .

# Las variables VITE_* se inyectan en build-time vía docker-compose (build.args).
# Vite las reemplaza en el bundle al momento de compilar (import.meta.env.VITE_*).
ARG VITE_API_URL
ARG VITE_COGNITO_REGION
ARG VITE_COGNITO_USER_POOL_ID
ARG VITE_COGNITO_USER_POOL_CLIENT_ID
ARG VITE_ADMIN_API_URL
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_COGNITO_REGION=$VITE_COGNITO_REGION
ENV VITE_COGNITO_USER_POOL_ID=$VITE_COGNITO_USER_POOL_ID
ENV VITE_COGNITO_USER_POOL_CLIENT_ID=$VITE_COGNITO_USER_POOL_CLIENT_ID
ENV VITE_ADMIN_API_URL=$VITE_ADMIN_API_URL

RUN npm run build

# --- Etapa de servido estático ---
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
