package com.ailabs.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de contrato HTTP de HU-01 (registro, login, wallet). Usa solo JUnit 5, @QuarkusTest y HttpClient.
 * Datos sintéticos y únicos por prueba. No hay endpoint de borrado: el almacén es en memoria y se descarta
 * al terminar el proceso. SUPUESTOS S1 a S9 del testing_report.md.
 */
@QuarkusTest
class RegistrationContractTest {

    private static final String PASSWORD = "483920";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestHTTPResource
    URL baseUrl;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<String> documentosCreados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        // No existe API de borrado; solo se descarta el seguimiento local. El estado en memoria
        // desaparece al terminar el proceso de pruebas.
        documentosCreados.clear();
    }

    // ---------- helpers ----------

    private record Resp(int status, String body) {
        JsonNode json() {
            try {
                return MAPPER.readTree(body);
            } catch (Exception e) {
                throw new IllegalStateException("Cuerpo no es JSON: " + body, e);
            }
        }
    }

    private Resp send(String method, String path, String json, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUrl.toURI().resolve(path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        HttpRequest.BodyPublisher publisher = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json);
        builder.method(method, publisher);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private static String documentoUnico() {
        return String.format("%08d", ThreadLocalRandom.current().nextInt(0, 100_000_000));
    }

    private static String correoUnico() {
        return "sintetico." + UUID.randomUUID() + "@example.com";
    }

    private static String cuerpo(String tipo, String documento, String correo, String contrasena) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("documentType", tipo);
        if (documento == null) {
            node.putNull("documentNumber");
        } else {
            node.put("documentNumber", documento);
        }
        if (correo == null) {
            node.putNull("email");
        } else {
            node.put("email", correo);
        }
        if (contrasena == null) {
            node.putNull("password");
        } else {
            node.put("password", contrasena);
        }
        return node.toString();
    }

    private static String loginBody(String tipo, String documento, String contrasena) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("documentType", tipo);
        node.put("documentNumber", documento);
        node.put("password", contrasena);
        return node.toString();
    }

    private Resp registrar(String tipo, String documento, String correo, String contrasena) throws Exception {
        return send("POST", "/api/v1/registrations", cuerpo(tipo, documento, correo, contrasena), null);
    }

    private Resp registrarValido(String documento) throws Exception {
        documentosCreados.add(documento);
        Resp r = registrar("DNI", documento, correoUnico(), PASSWORD);
        assertTrue(r.status() == 200 || r.status() == 201, "registro esperado 200/201 pero fue " + r.status()
                + " cuerpo=" + r.body());
        return r;
    }

    private static String idDeRegistro(JsonNode json) {
        for (String clave : new String[] {"registrationId", "id", "sessionId", "onboardingId"}) {
            if (json.hasNonNull(clave)) {
                return json.get(clave).asText();
            }
        }
        return null;
    }

    private void assertValidacionConCampo(Resp r, String campo) {
        assertEquals(422, r.status(), "cuerpo=" + r.body());
        JsonNode json = r.json();
        assertEquals("VALIDATION_ERROR", json.path("code").asText());
        String errores = json.path("details").path("errors").toString();
        assertTrue(errores.contains(campo), "errores deben mencionar " + campo + " pero fueron " + errores);
    }

    // ---------- CA-1 ----------

    @Test
    void ca1_positivo_registroDevuelvePasos() throws Exception {
        Resp r = registrarValido(documentoUnico());
        JsonNode json = r.json();
        assertEquals(2, json.path("currentStep").asInt());
        assertEquals(2, json.path("totalSteps").asInt());
    }

    // ---------- CA-2 ----------

    @Test
    void ca2_negativo_camposAusentes() throws Exception {
        Resp r = send("POST", "/api/v1/registrations", "{}", null);
        assertEquals(422, r.status(), "cuerpo=" + r.body());
        JsonNode json = r.json();
        assertEquals("VALIDATION_ERROR", json.path("code").asText());
        String errores = json.path("details").path("errors").toString();
        for (String campo : new String[] {"documentType", "documentNumber", "email", "password"}) {
            assertTrue(errores.contains(campo), "falta error para " + campo + ": " + errores);
        }
    }

    @Test
    void ca2_negativo_noCreaClienteTrasValidacionFallida() throws Exception {
        String doc = documentoUnico();
        Resp reg = registrar("DNI", doc, correoUnico(), "12345");
        assertValidacionConCampo(reg, "password");
        Resp login = send("POST", "/api/v1/auth/login", loginBody("DNI", doc, "12345"), null);
        assertEquals(401, login.status(), "cuerpo=" + login.body());
        assertEquals("INVALID_CREDENTIALS", login.json().path("code").asText());
    }

    // ---------- CA-3 ----------

    @Test
    void ca3_negativo_walletSinBearer() throws Exception {
        Resp r = send("GET", "/api/v1/wallets/me", null, null);
        assertEquals(401, r.status(), "cuerpo=" + r.body());
        assertEquals("UNAUTHENTICATED", r.json().path("code").asText());
    }

    @Test
    void ca3_negativo_walletBearerInvalido() throws Exception {
        Resp r = send("GET", "/api/v1/wallets/me", null, "Bearer token-inexistente-" + UUID.randomUUID());
        assertEquals(401, r.status(), "cuerpo=" + r.body());
        assertEquals("UNAUTHENTICATED", r.json().path("code").asText());
    }

    // ---------- CA-7 ----------

    @Test
    void ca7_positivo_documentoConCerosIniciales() throws Exception {
        // Valor de la tabla de datos: 00123456 (si ya existe por otra corrida, el 409 indica duplicado)
        String doc = "00123456";
        documentosCreados.add(doc);
        Resp r = registrar("DNI", doc, correoUnico(), PASSWORD);
        assertTrue(r.status() == 200 || r.status() == 201 || r.status() == 409,
                "status inesperado " + r.status() + " cuerpo=" + r.body());
        assertNotEquals(422, r.status());
        assertNotEquals(400, r.status());
    }

    @Test
    void ca7_borde_documento7Digitos() throws Exception {
        assertValidacionConCampo(registrar("DNI", "1234567", correoUnico(), PASSWORD), "documentNumber");
    }

    @Test
    void ca7_borde_documento9Digitos() throws Exception {
        assertValidacionConCampo(registrar("DNI", "123456789", correoUnico(), PASSWORD), "documentNumber");
    }

    @Test
    void ca7_borde_documentoVacioYNulo() throws Exception {
        assertValidacionConCampo(registrar("DNI", "", correoUnico(), PASSWORD), "documentNumber");
        assertValidacionConCampo(registrar("DNI", null, correoUnico(), PASSWORD), "documentNumber");
    }

    @Test
    void ca7_negativo_tipoDocumentoInvalido() throws Exception {
        assertValidacionConCampo(registrar("XX", documentoUnico(), correoUnico(), PASSWORD), "documentType");
    }

    @Test
    void ca7_negativo_documentoNumericoJson() throws Exception {
        String json = "{\"documentType\":\"DNI\",\"documentNumber\":12345678,\"email\":\"" + correoUnico()
                + "\",\"password\":\"" + PASSWORD + "\"}";
        Resp r = send("POST", "/api/v1/registrations", json, null);
        assertEquals(400, r.status(), "cuerpo=" + r.body());
        assertEquals("INVALID_REQUEST", r.json().path("code").asText());
    }

    @Test
    void ca7_borde_documentoDuplicado() throws Exception {
        String doc = documentoUnico();
        registrarValido(doc);
        Resp segundo = registrar("DNI", doc, correoUnico(), PASSWORD);
        assertEquals(409, segundo.status(), "SUPUESTO S5; cuerpo=" + segundo.body());
    }

    // ---------- CA-8 ----------

    @Test
    void ca8_borde_contrasena() throws Exception {
        String[] invalidas = {"12345", "1234567", "12345a", ""};
        for (String contrasena : invalidas) {
            assertValidacionConCampo(registrar("DNI", documentoUnico(), correoUnico(), contrasena), "password");
        }
        assertValidacionConCampo(registrar("DNI", documentoUnico(), correoUnico(), null), "password");
    }

    @Test
    void ca8_negativo_correoInvalido() throws Exception {
        assertValidacionConCampo(registrar("DNI", documentoUnico(), "usuario@", PASSWORD), "email");
        assertValidacionConCampo(registrar("DNI", documentoUnico(), "", PASSWORD), "email");
        assertValidacionConCampo(registrar("DNI", documentoUnico(), null, PASSWORD), "email");
    }

    // ---------- CA-9 ----------

    @Test
    void ca9_negativo_documentoConCaracteresInvalidos() throws Exception {
        String[] invalidos = {"ABC12345", "7375 7451", "7375745-", "7375 74A1", "1234567890ab"};
        for (String doc : invalidos) {
            assertValidacionConCampo(registrar("DNI", doc, correoUnico(), PASSWORD), "documentNumber");
        }
    }

    // ---------- CA-13 ----------

    @Test
    void ca13_negativo_mensajesDelServidorSinTest() throws Exception {
        List<Resp> errores = new ArrayList<>();
        errores.add(registrar("DNI", "ABC12345", correoUnico(), PASSWORD));
        errores.add(registrar("DNI", "1234567", "usuario@", "12345"));
        errores.add(send("POST", "/api/v1/auth/login", loginBody("DNI", documentoUnico(), PASSWORD), null));
        errores.add(send("POST", "/api/v1/registrations/" + UUID.randomUUID() + "/otp/verify",
                "{\"otp\":\"000000\"}", null));
        for (Resp r : errores) {
            assertTrue(r.status() >= 400 && r.status() < 500, "status " + r.status() + " cuerpo=" + r.body());
            String mensaje = r.json().path("message").asText("");
            assertFalse(mensaje.toLowerCase().contains("test"), "mensaje con referencia a test: " + mensaje);
            String detalles = r.json().path("details").toString().toLowerCase();
            assertFalse(detalles.contains("test"), "detalles con referencia a test: " + detalles);
        }
    }

    // ---------- CA-14 ----------

    @Test
    void ca14_negativo_otpIncorrecto() throws Exception {
        String doc = documentoUnico();
        JsonNode registro = registrarValido(doc).json();
        String id = idDeRegistro(registro);
        assertNotNull(id, "SUPUESTO S3: no se encontró el id del registro en " + registro);
        Resp r = send("POST", "/api/v1/registrations/" + id + "/otp/verify", "{\"otp\":\"000000\"}", null);
        assertTrue(r.status() == 400 || r.status() == 422, "SUPUESTO S6; status " + r.status()
                + " cuerpo=" + r.body());
        assertEquals("OTP_INVALID", r.json().path("code").asText());
        Resp login = send("POST", "/api/v1/auth/login", loginBody("DNI", doc, PASSWORD), null);
        assertEquals(403, login.status(), "cliente sigue sin validar; cuerpo=" + login.body());
        assertEquals("CUSTOMER_NOT_VALIDATED", login.json().path("code").asText());
    }

    @Test
    void ca14_negativo_otpRegistroInexistente() throws Exception {
        Resp r = send("POST", "/api/v1/registrations/" + UUID.randomUUID() + "/otp/verify",
                "{\"otp\":\"000000\"}", null);
        assertEquals(404, r.status(), "cuerpo=" + r.body());
    }

    // ---------- CA-15 ----------

    @Test
    void ca15_negativo_loginClienteNoValidado() throws Exception {
        String doc = documentoUnico();
        registrarValido(doc);
        Resp r = send("POST", "/api/v1/auth/login", loginBody("DNI", doc, PASSWORD), null);
        assertEquals(403, r.status(), "cuerpo=" + r.body());
        assertEquals("CUSTOMER_NOT_VALIDATED", r.json().path("code").asText());
        assertFalse(r.body().toLowerCase().contains("token"), "no debe emitirse token: " + r.body());
    }

    @Test
    void ca15_negativo_credencialesIncorrectas() throws Exception {
        String doc = documentoUnico();
        registrarValido(doc);
        Resp contrasenaErronea = send("POST", "/api/v1/auth/login", loginBody("DNI", doc, "000111"), null);
        Resp documentoInexistente = send("POST", "/api/v1/auth/login",
                loginBody("DNI", documentoUnico(), PASSWORD), null);
        assertEquals(401, contrasenaErronea.status(), "cuerpo=" + contrasenaErronea.body());
        assertEquals(401, documentoInexistente.status(), "cuerpo=" + documentoInexistente.body());
        assertEquals("INVALID_CREDENTIALS", contrasenaErronea.json().path("code").asText());
        assertEquals("INVALID_CREDENTIALS", documentoInexistente.json().path("code").asText());
        assertEquals(contrasenaErronea.json().path("message").asText(),
                documentoInexistente.json().path("message").asText(),
                "el mensaje no debe revelar qué dato falló");
    }

    @Test
    void ca15_borde_mismoNumeroDistintoTipo() throws Exception {
        String doc = documentoUnico();
        registrarValido(doc);
        Resp r = send("POST", "/api/v1/auth/login", loginBody("CE", doc, PASSWORD), null);
        assertEquals(401, r.status(), "cuerpo=" + r.body());
        assertEquals("INVALID_CREDENTIALS", r.json().path("code").asText());
    }

    @Test
    void ca15_negativo_bloqueoTrasIntentosFallidos() throws Exception {
        // Valor provisional (P6): 5 intentos fallidos antes del bloqueo.
        String doc = documentoUnico();
        registrarValido(doc);
        for (int i = 1; i <= 5; i++) {
            Resp r = send("POST", "/api/v1/auth/login", loginBody("DNI", doc, "000111"), null);
            assertEquals(401, r.status(), "intento " + i + " cuerpo=" + r.body());
        }
        Resp bloqueado = send("POST", "/api/v1/auth/login", loginBody("DNI", doc, "000111"), null);
        assertEquals(429, bloqueado.status(), "cuerpo=" + bloqueado.body());
    }

    // ---------- CA-16 ----------

    @Test
    void ca16_negativo_logoutSinToken() throws Exception {
        Resp r = send("POST", "/api/v1/auth/logout", null, null);
        assertTrue(r.status() >= 400 && r.status() < 500, "SUPUESTO S9; status " + r.status()
                + " cuerpo=" + r.body());
    }

    // ---------- CA-17 ----------

    @Test
    void ca17_integracion_respuestaNoContieneSecretos() throws Exception {
        String doc = documentoUnico();
        Resp registro = registrarValido(doc);
        String id = idDeRegistro(registro.json());
        List<Resp> respuestas = new ArrayList<>();
        respuestas.add(registro);
        if (id != null) {
            respuestas.add(send("GET", "/api/v1/registrations/" + id, null, null));
        }
        respuestas.add(registrar("DNI", "1234567", correoUnico(), PASSWORD));
        for (Resp r : respuestas) {
            String texto = r.body();
            for (String clave : new String[] {"\"password\"", "\"passwordHash\"", "\"otp\"", "\"otpHash\""}) {
                assertFalse(texto.contains(clave), "la respuesta no debe contener " + clave + ": " + texto);
            }
        }
    }
}
