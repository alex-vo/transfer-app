package com.transferapp.e2e;

import com.google.gson.Gson;
import com.transferapp.TransferApp;
import com.transferapp.dto.AccountStateDTO;
import com.transferapp.dto.ResponseMessageDTO;
import com.transferapp.dto.TransferDTO;
import io.restassured.RestAssured;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

public class E2ETest {

    private static final Long FIRST_ACCOUNT_ID = 1L;
    private static final Long SECOND_ACCOUNT_ID = 2L;
    private static final Gson GSON = new Gson();

    @BeforeAll
    public static void setup() {
        TransferApp.main(new String[]{});
        RestAssured.port = 4567;
        Awaitility.await().atMost(10L, TimeUnit.SECONDS).pollInterval(1L, TimeUnit.SECONDS).until(() -> {
            try {
                RestAssured.given().get("/healthcheck").then().statusCode(200);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Test
    public void testGetAccountById() {
        AccountStateDTO firstAccountState = performGetAccountRequest(FIRST_ACCOUNT_ID);
        assertThat(firstAccountState.getId(), is(FIRST_ACCOUNT_ID));
        assertThat(firstAccountState.getBalance(), is(BigDecimal.valueOf(4.5)));
        AccountStateDTO secondAccountState = performGetAccountRequest(SECOND_ACCOUNT_ID);
        assertThat(secondAccountState.getId(), is(SECOND_ACCOUNT_ID));
        assertThat(secondAccountState.getBalance(), is(BigDecimal.valueOf(3.5)));

        RestAssured.given().get("/account/3")
                .then().statusCode(404);
        RestAssured.given().get("/account")
                .then().statusCode(404);
    }

    private AccountStateDTO performGetAccountRequest(Long secondAccountId) {
        return RestAssured.given().get("/account/" + secondAccountId)
                .then().statusCode(200)
                .extract().as(AccountStateDTO.class);
    }

    @Test
    public void testTransferFunds_happyPath() {
        BigDecimal fistAccountInitialAmount = performGetAccountRequest(FIRST_ACCOUNT_ID).getBalance();
        BigDecimal secondAccountInitialAmount = performGetAccountRequest(SECOND_ACCOUNT_ID).getBalance();
        assertThat(fistAccountInitialAmount, greaterThan(BigDecimal.ONE));
        RestAssured.given().body(GSON.toJson(prepareTransferDTO(FIRST_ACCOUNT_ID, SECOND_ACCOUNT_ID, BigDecimal.ONE)))
                .post("/transfer")
                .then().statusCode(200);

        assertThat(performGetAccountRequest(FIRST_ACCOUNT_ID).getBalance(), is(fistAccountInitialAmount.subtract(BigDecimal.ONE)));
        assertThat(performGetAccountRequest(SECOND_ACCOUNT_ID).getBalance(), is(secondAccountInitialAmount.add(BigDecimal.ONE)));
    }

    @Test
    public void testTransferFunds_fail_validationErrors() {
        TransferDTO dto = prepareTransferDTO(FIRST_ACCOUNT_ID, SECOND_ACCOUNT_ID, BigDecimal.ONE);
        dto.setAmount(null);
        ResponseMessageDTO responseMessageDTO = RestAssured.given().body(GSON.toJson(dto))
                .post("/transfer")
                .then().statusCode(400)
                .extract().as(ResponseMessageDTO.class);
        assertThat(responseMessageDTO.getMessage(), is("amount cannot be empty"));
        dto.setAmount(BigDecimal.ONE);
        dto.setFrom(null);
        responseMessageDTO = RestAssured.given().body(GSON.toJson(dto))
                .post("/transfer")
                .then().statusCode(400)
                .extract().as(ResponseMessageDTO.class);
        assertThat(responseMessageDTO.getMessage(), is("source account cannot be empty"));
        dto.setFrom(FIRST_ACCOUNT_ID);
        dto.setTo(null);
        responseMessageDTO = RestAssured.given().body(GSON.toJson(dto))
                .post("/transfer")
                .then().statusCode(400)
                .extract().as(ResponseMessageDTO.class);
        assertThat(responseMessageDTO.getMessage(), is("destination account cannot be empty"));
        dto.setTo(FIRST_ACCOUNT_ID);
        responseMessageDTO = RestAssured.given().body(GSON.toJson(dto))
                .post("/transfer")
                .then().statusCode(400)
                .extract().as(ResponseMessageDTO.class);
        assertThat(responseMessageDTO.getMessage(), is("Cannot transfer to self"));
        dto.setTo(SECOND_ACCOUNT_ID);
        dto.setAmount(BigDecimal.valueOf(-1L));
        responseMessageDTO = RestAssured.given().body(GSON.toJson(dto))
                .post("/transfer")
                .then().statusCode(400)
                .extract().as(ResponseMessageDTO.class);
        assertThat(responseMessageDTO.getMessage(), is("transfer amount has to be positive"));
    }

    @Test
    public void testTransferFunds_fail_nonExistingAccounts() {
        TransferDTO dto = prepareTransferDTO(100L, 101L, BigDecimal.ONE);
        ResponseMessageDTO responseMessageDTO = RestAssured.given().body(GSON.toJson(dto))
                .post("/transfer")
                .then().statusCode(404)
                .extract().as(ResponseMessageDTO.class);
        assertThat(responseMessageDTO.getMessage(), is(String.format("account %d not found", 100L)));
        dto.setFrom(FIRST_ACCOUNT_ID);
        responseMessageDTO = RestAssured.given().body(GSON.toJson(dto))
                .post("/transfer")
                .then().statusCode(404)
                .extract().as(ResponseMessageDTO.class);
        assertThat(responseMessageDTO.getMessage(), is(String.format("account %d not found", 101L)));
    }

    @Test
    public void testTransferFunds_fail_insufficientFunds() {
        TransferDTO dto = prepareTransferDTO(FIRST_ACCOUNT_ID, SECOND_ACCOUNT_ID, BigDecimal.valueOf(10));
        ResponseMessageDTO responseMessageDTO = RestAssured.given().body(GSON.toJson(dto))
                .post("/transfer")
                .then().statusCode(400)
                .extract().as(ResponseMessageDTO.class);
        assertThat(responseMessageDTO.getMessage(), is("insufficient funds"));
    }

    private TransferDTO prepareTransferDTO(Long from, Long to, BigDecimal amount) {
        TransferDTO dto = new TransferDTO();
        dto.setFrom(from);
        dto.setTo(to);
        dto.setAmount(amount);
        return dto;
    }

}
