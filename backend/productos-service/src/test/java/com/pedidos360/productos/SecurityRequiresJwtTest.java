package com.pedidos360.productos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba obligatoria de la rúbrica: "sin token -> 401".
 * El JwtDecoder real (que descarga el JWKS de Cognito) se reemplaza por un mock: la cadena de seguridad
 * es la misma de producción (SecurityConfig), así que una petición sin header Authorization debe ser rechazada.
 * La validación de tokens reales (firma, issuer, expiración, 403 por rol) se prueba manualmente contra
 * Cognito siguiendo el README, sección "Pruebas".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRequiresJwtTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void sinTokenRespondeUnauthorized() throws Exception {
        mockMvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
    }
}
