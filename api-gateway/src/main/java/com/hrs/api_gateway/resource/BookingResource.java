package com.hrs.api_gateway.resource;

import com.hrs.api_gateway.exception.BookingNotFoundException;
import com.hrs.api_gateway.exception.InsufficientCapacityException;
import com.hrs.api_gateway.model.BookingDTO;
import com.hrs.api_gateway.service.BookingService;
import com.hrs.api_gateway.utils.CommonHelper;
import io.quarkus.redis.client.RedisClient;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Path("/api/v1/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookingResource {

    private static final Logger LOG = Logger.getLogger(BookingResource.class);

    @Inject
    BookingService bookingService;

    @Inject
    RedisClient redisClient;

    @Context
    UriInfo uriInfo;

    @GET
    @Path("/search")
    @Operation(summary = "Search bookings by criteria (Elasticsearch)", description = "Searches bookings using Elasticsearch based on user ID, hotel ID, check-in date, and check-out date.")
    @APIResponse(responseCode = "200", description = "Successful search", content = @Content(mediaType = "application/json", schema = @Schema(type = SchemaType.ARRAY, implementation = BookingDTO.class)))
    public List<BookingDTO> searchBookings(
            @Parameter(description = "Filter by User ID") @QueryParam("userId") Long userId,
            @Parameter(description = "Filter by Hotel ID") @QueryParam("hotelId") Long hotelId,
            @Parameter(description = "Filter by check-in date (YYYY-MM-DD), find bookings with check-in date on or before this date") @QueryParam("checkinDate") String checkinDateStr,
            @Parameter(description = "Filter by check-out date (YYYY-MM-DD), find bookings with check-out date on or after this date") @QueryParam("checkoutDate") String checkoutDateStr
    ) throws IOException {
        LocalDate checkinDate = CommonHelper.parseLocalDate(checkinDateStr);
        LocalDate checkoutDate = CommonHelper.parseLocalDate(checkoutDateStr);

        return bookingService.searchBookingsByCriteria(userId, hotelId, checkinDate, checkoutDate);
    }

    @GET
    @Path("/user/{userId}")
    @Operation(summary = "Get bookings by User ID", description = "Retrieves all bookings associated with a specific user ID from the database.")
    @APIResponse(responseCode = "200", description = "Successful retrieval", content = @Content(mediaType = "application/json", schema = @Schema(type = SchemaType.ARRAY, implementation = BookingDTO.class)))
    public List<BookingDTO> getBookingsByUserId(@Parameter(description = "User ID to filter bookings", required = true) @PathParam("userId") Long userId) {
        return bookingService.getBookingsByUserId(userId);
    }

    @GET
    @Path("/hotel/{hotelId}")
    @Operation(summary = "Get bookings by Hotel ID", description = "Retrieves all bookings for a specific hotel ID from the database.")
    @APIResponse(responseCode = "200", description = "Successful retrieval", content = @Content(mediaType = "application/json", schema = @Schema(type = SchemaType.ARRAY, implementation = BookingDTO.class)))
    public List<BookingDTO> getBookingsByHotelId(@Parameter(description = "Hotel ID to filter bookings", required = true) @PathParam("hotelId") Long hotelId) {
        return bookingService.getBookingsByHotelId(hotelId);
    }

    @POST
    @Transactional
    @Operation(summary = "Create a new booking", description = "Creates a new booking record, validates capacity, and persists it in the database.")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Booking created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingDTO.class))),
            @APIResponse(responseCode = "400", description = "Bad Request - Invalid input data or potential XSS attack", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "409", description = "Conflict - Insufficient hotel capacity", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public Response createBooking(@RequestBody(description = "Booking details to be created", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingDTO.class))) BookingDTO bookingDTO,
            @Context UriInfo uriInfo, @Context ContainerRequestContext requestContext) {

        String idempotencyKey = (String) requestContext.getProperty("idempotencyKey");

        if (!CommonHelper.isValidBookingInput(bookingDTO)) { // Call static method in ValidationHelper
            LOG.warn("XSS detected in createBooking request");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid input: Potential XSS detected.")
                    .build();
        }

        try {
            BookingDTO createdBooking = bookingService.createBooking(bookingDTO);

            // Store the successful response in Redis
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.put("status", Response.Status.CREATED.getStatusCode());
            jsonResponse.put("body", createdBooking.toString());

            redisClient.setex(idempotencyKey, String.valueOf(Duration.ofMinutes(10).getSeconds()), jsonResponse.encode()); // Expire after 10 minutes

            return Response.status(Response.Status.CREATED).entity(createdBooking).build();

        } catch (InsufficientCapacityException capacityException) { // Catch InsufficientCapacityException
            LOG.warn("Insufficient capacity for booking", capacityException);
            return Response.status(Response.Status.CONFLICT) // 409 Conflict status
                    .entity("Capacity Conflict") // Standard error response
                    .build();

        } catch (Exception e) {
            LOG.error("Error creating booking", e);
            // Handle the error appropriately (e.g., return an error response)
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.put("status", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
            jsonResponse.put("body", "Error creating booking");

            redisClient.setex(idempotencyKey, String.valueOf(Duration.ofMinutes(10).getSeconds()), jsonResponse.encode()); // Expire after 10 minutes

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error creating booking").build();
        }
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get booking details by ID", description = "Retrieves complete information for a specific booking using its ID.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Booking details retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingDTO.class))),
            @APIResponse(responseCode = "404", description = "Booking not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public BookingDTO getBooking(@Parameter(description = "Unique identifier of the booking", required = true) @PathParam("id") Long id) {
        try {
            return bookingService.getBooking(id);
        } catch (BookingNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Update booking information", description = "Updates the details of an existing booking. Allows modification of check-in/out dates, number of guests, and total price.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Booking updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingDTO.class))),
            @APIResponse(responseCode = "400", description = "Bad Request - Invalid input data or potential XSS attack", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "404", description = "Booking not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public BookingDTO updateBooking(@Parameter(description = "ID of the booking to be updated", required = true) @PathParam("id") Long id,
                                    @RequestBody(description = "Updated booking details", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingDTO.class))) BookingDTO bookingDTO) {
        if (!CommonHelper.isValidBookingInput(bookingDTO)) { // Call static method in ValidationHelper
            LOG.warn("XSS detected in updateBooking request");
            throw new BadRequestException("Invalid input: Potential XSS detected."); // Use BadRequestException for PUT too
        }

        try {
            return bookingService.updateBooking(id, bookingDTO);
        } catch (BookingNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Delete a booking", description = "Cancels and deletes a booking record from the database.")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Booking deleted successfully - No Content"), // No Content for successful DELETE
            @APIResponse(responseCode = "404", description = "Booking not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public Response deleteBooking(@Parameter(description = "ID of the booking to be deleted", required = true) @PathParam("id") Long id) {
        boolean deleted = bookingService.deleteBooking(id);
        if (deleted) {
            return Response.noContent().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}