package com.hrs.api_gateway.resource;

import com.hrs.api_gateway.exception.HotelNotFoundException;
import com.hrs.api_gateway.model.HotelDTO;
import com.hrs.api_gateway.service.HotelService;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/hotels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HotelResource {

    private static final Logger LOG = Logger.getLogger(HotelResource.class);

    @Inject
    HotelService hotelService;

    @Inject
    RedisClient redisClient;

    @Context
    UriInfo uriInfo;

    @GET
    @Path("/search")
    @Operation(summary = "Search hotels by keywords", description = "Searches hotels based on keywords in name, city, or address using Elasticsearch.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Successful search", content = @Content(mediaType = "application/json", schema = @Schema(type = SchemaType.ARRAY, implementation = HotelDTO.class))),
            @APIResponse(responseCode = "400", description = "Bad Request - Invalid search query", content = @Content(mediaType = "text/plain")),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public List<HotelDTO> searchHotelsByQuery(@Parameter(description = "Keywords to search for (e.g., 'Luxury Paris Hotel')", required = false) @QueryParam("query") String query) {
        try {
            LOG.debug(query);
            if (query == null || query.trim().isEmpty()) {
                throw new BadRequestException("Search query cannot be empty");
            }
            List<HotelDTO> hotels = hotelService.searchHotels(query);
            return hotels.stream()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOG.error("Error searching hotel", e);
            return Collections.emptyList();
        }
    }


    @POST
    @Transactional
    @Operation(summary = "Create a new hotel", description = "Creates a new hotel record in the database and indexes it in Elasticsearch.")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Hotel created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HotelDTO.class))),
            @APIResponse(responseCode = "400", description = "Bad Request - Invalid input data or potential XSS attack", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public Response createHotel(@RequestBody(description = "Hotel details to be created", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = HotelDTO.class))) HotelDTO hotelDTO, @Context UriInfo uriInfo, @Context ContainerRequestContext requestContext) {

        String idempotencyKey = (String) requestContext.getProperty("idempotencyKey");

        if (!CommonHelper.isValidHotelInput(hotelDTO)) { // Call static method in ValidationHelper
            LOG.warn("XSS detected in createHotel request");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid input: Potential XSS detected in hotel data.") // More specific error message
                    .build();
        }

        try {
            HotelDTO createdHotel = hotelService.createHotel(hotelDTO);

            // Store the successful response in Redis
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.put("status", Response.Status.CREATED.getStatusCode());
            jsonResponse.put("body", createdHotel.toString()); // Consider a better JSON serialization here for DTO

            redisClient.setex(idempotencyKey, String.valueOf(Duration.ofMinutes(10).getSeconds()), jsonResponse.encode()); // Expire after 10 minutes

            return Response.status(Response.Status.CREATED).entity(createdHotel).build();

        } catch (Exception e) {
            LOG.error("Error creating hotel", e);
            // Handle the error appropriately (e.g., return an error response)
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.put("status", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
            jsonResponse.put("body", "Error creating hotel");

            redisClient.setex(idempotencyKey, String.valueOf(Duration.ofMinutes(10).getSeconds()), jsonResponse.encode()); // Expire after 10 minutes

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error creating hotel").build();
        }
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get hotel details by ID", description = "Retrieves complete information for a specific hotel, including name, city, and address.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Hotel details retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HotelDTO.class))),
            @APIResponse(responseCode = "404", description = "Hotel not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public HotelDTO getHotel(@Parameter(description = "Unique identifier of the hotel", required = true) @PathParam("id") Long id) {
        try {
            return hotelService.getHotel(id);
        } catch (HotelNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Update hotel information", description = "Updates the details of an existing hotel. Allows modification of name, city, and address.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Hotel updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HotelDTO.class))),
            @APIResponse(responseCode = "400", description = "Bad Request - Invalid input data or potential XSS attack", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "404", description = "Hotel not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HotelNotFoundException.class))),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public HotelDTO updateHotel(@Parameter(description = "ID of the hotel to be updated", required = true) @PathParam("id") Long id,
                                  @RequestBody(description = "Updated hotel details", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = HotelDTO.class))) HotelDTO hotelDTO) {
        if (!CommonHelper.isValidHotelInput(hotelDTO)) { // Call static method in ValidationHelper
            LOG.warn("XSS detected in updateHotel request");
            throw new BadRequestException("Invalid input: Potential XSS detected in hotel data."); // More specific error message
        }

        try {
            return hotelService.updateHotel(id, hotelDTO);
        } catch (HotelNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Delete a hotel", description = "Deletes a hotel record from the database. Note: Associated bookings might need to be handled accordingly (e.g., cancellation).")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Hotel deleted successfully - No Content"), // No Content for successful DELETE
            @APIResponse(responseCode = "404", description = "Hotel not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
            @APIResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Response.class)))
    })
    public Response deleteHotel(@Parameter(description = "ID of the hotel to be deleted", required = true) @PathParam("id") Long id) {
        boolean deleted = hotelService.deleteHotel(id);
        if (deleted) {
            return Response.noContent().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}