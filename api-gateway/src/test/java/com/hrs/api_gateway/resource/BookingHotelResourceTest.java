package com.hrs.api_gateway.resource;

import com.hrs.api_gateway.model.BookingDTO;
import com.hrs.api_gateway.model.HotelDTO;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class BookingHotelResourceTest {

    private static final ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.17.0"))
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false");

    @BeforeAll
    static void startContainers() {
        elasticsearch.start();
        System.setProperty("quarkus.elasticsearch.hosts", elasticsearch.getHttpHostAddress());
    }

    @Test
    public void testSearchBookings() {
        given()
            .queryParam("userId", 1)
            .queryParam("hotelId", 1)
            .when()
            .get("/api/v1/bookings/search")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    public void testGetBookingById() {
        given()
            .pathParam("id", 1)
            .when()
            .get("/api/v1/bookings/{id}")
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test
    public void testCreateBooking() {
        BookingDTO booking = new BookingDTO();
        // Populate booking object with necessary test data

        given()
            .contentType(ContentType.JSON)
            .body(booking)
            .when()
            .post("/api/v1/bookings")
            .then()
            .statusCode(anyOf(is(201), is(400), is(409)));
    }

    @Test
    public void testDeleteBooking() {
        given()
            .pathParam("id", 1)
            .when()
            .delete("/api/v1/bookings/{id}")
            .then()
            .statusCode(anyOf(is(204), is(404)));
    }

    @Test
    public void testSearchHotels() {
        given()
            .queryParam("query", "New")
            .when()
            .get("/api/v1/hotels/search")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    public void testGetHotelById() {
        given()
            .pathParam("id", 1)
            .when()
            .get("/api/v1/hotels/{id}")
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test
    public void testCreateHotel() {
        HotelDTO hotel = new HotelDTO();
        // Populate hotel object with necessary test data

        given()
            .contentType(ContentType.JSON)
            .body(hotel)
            .when()
            .post("/api/v1/hotels")
            .then()
            .statusCode(anyOf(is(201), is(400)));
    }

    @Test
    public void testDeleteHotel() {
        given()
            .pathParam("id", 1)
            .when()
            .delete("/api/v1/hotels/{id}")
            .then()
            .statusCode(anyOf(is(204), is(404)));
    }
}
