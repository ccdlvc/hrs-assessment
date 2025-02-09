package com.hrs.api_gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hrs.api_gateway.entity.Booking;
import com.hrs.api_gateway.entity.BookingStatus;
import com.hrs.api_gateway.entity.Hotel;
import com.hrs.api_gateway.entity.User;
import com.hrs.api_gateway.exception.BookingNotFoundException;
import com.hrs.api_gateway.exception.HotelNotFoundException;
import com.hrs.api_gateway.exception.InsufficientCapacityException;
import com.hrs.api_gateway.model.BookingDTO;
import com.hrs.api_gateway.repository.BookingRepository;
import com.hrs.api_gateway.repository.HotelRepository;
import com.hrs.api_gateway.repository.UserRepository;
import com.hrs.api_gateway.utils.CommonHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class BookingService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Inject
    BookingRepository bookingRepository;
    @Inject
    HotelRepository hotelRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    RestClient restClient;

    private final String BOOKING_INDEX = "connect.hrs_booking.bookings";

    public List<BookingDTO> searchBookingsByCriteria(Long userId, Long hotelId, LocalDate checkinDate, LocalDate checkoutDate) throws IOException {
        return searchBookingsInElasticsearchRestClientByCriteria(userId, hotelId, checkinDate, checkoutDate);
    }

    private List<BookingDTO> searchBookingsInElasticsearchRestClientByCriteria(Long userId, Long hotelId, LocalDate checkinDate, LocalDate checkoutDate) throws IOException {

        String searchQueryJson = buildSearchQueryJson(userId, hotelId, checkinDate, checkoutDate);
        HttpEntity entity = new NStringEntity(searchQueryJson, ContentType.APPLICATION_JSON);

        Request request = new Request("GET", "/" + BOOKING_INDEX + "/_search"); // Index name: "bookings"
        request.setEntity(entity);

        Response response = restClient.performRequest(request);

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IOException("Elasticsearch booking search failed: " + response.getStatusLine());
        }

        String responseBody = org.apache.http.util.EntityUtils.toString(response.getEntity());
        com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(responseBody);

        return mapRestClientSearchResponseToBookingDTOs(jsonResponse);
    }

    private String buildSearchQueryJson(Long userId, Long hotelId, LocalDate checkinDate, LocalDate checkoutDate) throws IOException {
        ObjectNode boolQueryNode = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode mustClauses = objectMapper.createArrayNode();
        com.fasterxml.jackson.databind.node.ArrayNode filterClauses = objectMapper.createArrayNode();

        if (userId != null) {
            mustClauses.add(objectMapper.createObjectNode().putPOJO("term", objectMapper.createObjectNode().put("user_id", userId))); // Search by User ID (exact match)
        }
        if (hotelId != null) {
            mustClauses.add(objectMapper.createObjectNode().putPOJO("term", objectMapper.createObjectNode().put("hotel_id", hotelId))); // Search by Hotel ID (exact match)
        }
        if (checkinDate != null) {
            filterClauses.add(createRangeQuery("check_in_date", formatDateForElasticsearch(checkinDate), null, true, true)); // gte checkinDate (Find bookings with check-in date ON or AFTER provided date) - ADJUSTED LOGIC
        }
        if (checkoutDate != null) {
            filterClauses.add(createRangeQuery("check_out_date", null, formatDateForElasticsearch(checkoutDate), true, true)); // lte checkoutDate (Find bookings with check-out date ON or BEFORE provided date) - ADJUSTED LOGIC
        }

        ObjectNode boolQuery = objectMapper.createObjectNode();
        if (!mustClauses.isEmpty()) {
            boolQuery.putPOJO("must", mustClauses);
        }
        if (!filterClauses.isEmpty()) {
            boolQuery.putPOJO("filter", objectMapper.createArrayNode().addAll(filterClauses));
        }
        boolQueryNode.set("bool", boolQuery);

        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.set("query", boolQueryNode);

        return objectMapper.writeValueAsString(rootNode);
    }

    private ObjectNode createRangeQuery(String field, String gte, String lte, boolean includeLower, boolean includeUpper) {
        ObjectNode rangeNode = objectMapper.createObjectNode();
        ObjectNode fieldRange = objectMapper.createObjectNode();
        if (gte != null) fieldRange.put("gte", gte); // gte (greater-than-or-equal-to) for start date
        if (lte != null) fieldRange.put("lte", lte); // lte (less-than-or-equal-to) for end date
        fieldRange.put("include_lower", includeLower);
        fieldRange.put("include_upper", includeUpper);
        rangeNode.set(field, fieldRange);
        return objectMapper.createObjectNode().set("range", rangeNode);
    }


    private String formatDateForElasticsearch(LocalDate date) {
        if (date == null) return null;
        return DateTimeFormatter.ISO_DATE.format(date);
    }

    private List<BookingDTO> mapRestClientSearchResponseToBookingDTOs(com.fasterxml.jackson.databind.JsonNode jsonResponse) {
        List<BookingDTO> bookingDTOs = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode hitsNode = jsonResponse.path("hits").path("hits");
        if (hitsNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode hit : hitsNode) {
                com.fasterxml.jackson.databind.JsonNode sourceNode = hit.path("_source");

                // Directly map fields from _source JSON to BookingDTO
                BookingDTO bookingDTO = new BookingDTO();
                bookingDTO.setId(sourceNode.path("id").asLong());
                bookingDTO.setHotelId(sourceNode.path("hotel_id").asLong());
                bookingDTO.setUserId(sourceNode.path("user_id").asLong());
                bookingDTO.setCheckinDate(CommonHelper.parseLocalDateTime(sourceNode.path("check_in_date").asText()));
                bookingDTO.setCheckoutDate(CommonHelper.parseLocalDateTime(sourceNode.path("check_out_date").asText()));
                bookingDTO.setNumberOfGuests(sourceNode.path("number_of_guests").asInt());
                bookingDTO.setTotalPrice(sourceNode.path("total_price").asLong());
                bookingDTO.setBookingStatus(BookingStatus.valueOf(sourceNode.path("booking_status").asText()));

                bookingDTOs.add(bookingDTO);
            }
        }
        return bookingDTOs;

    }

    public List<BookingDTO> getBookingsByUserId(Long userId) {
        List<Booking> bookings = bookingRepository.findByUserId(userId);
        return bookings.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<BookingDTO> getBookingsByHotelId(Long hotelId) {
        List<Booking> bookings = bookingRepository.findByHotelId(hotelId);
        return bookings.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(value = TxType.REQUIRED, rollbackOn = InsufficientCapacityException.class)
    // Serializable isolation for race condition protection
    public BookingDTO createBooking(BookingDTO bookingDTO) {
        Hotel hotel = hotelRepository.findById(bookingDTO.getHotelId());
        if (hotel == null) {
            throw new HotelNotFoundException("Hotel not found with id: " + bookingDTO.getHotelId());
        }

        User user = userRepository.findById(bookingDTO.getUserId());
        if (user == null) {
            throw new IllegalArgumentException("User not found with id: " + bookingDTO.getUserId());
        }

        // Capacity Check Logic
        Hotel hotelForUpdate = hotelRepository.findByIdForUpdate(bookingDTO.getHotelId()); // Custom method with SELECT FOR UPDATE
        if (hotelForUpdate == null) {
            throw new HotelNotFoundException("Hotel not found with id: " + bookingDTO.getHotelId());
        }

        int bookedCapacity = getBookedCapacityForHotelAndDates(hotelForUpdate.getId(), bookingDTO.getCheckinDate(), bookingDTO.getCheckoutDate());
        int requestedCapacity = bookingDTO.getNumberOfGuests();
        int hotelCapacity = hotelForUpdate.getCapacity();
        int availableCapacity = hotelCapacity - bookedCapacity;

        if (requestedCapacity > availableCapacity) {
            throw new InsufficientCapacityException("Hotel has insufficient capacity for the requested number of guests.");
        }

        Booking booking = convertToEntity(bookingDTO);
        booking.setHotel(hotel);
        booking.setUser(user);
        booking.setBookingStatus(BookingStatus.PENDING);
        bookingRepository.persist(booking);
        return convertToDTO(booking);
    }

    private int getBookedCapacityForHotelAndDates(Long hotelId, LocalDateTime checkinDate, LocalDateTime checkoutDate) {
        // Count CONFIRMED bookings for the hotel within the given date range
        List<Booking> confirmedBookings = bookingRepository.find("hotel.id = ?1 and bookingStatus = ?2 and checkoutDate > ?3 and checkinDate < ?4",
                hotelId, BookingStatus.PENDING, checkinDate, checkoutDate).list();

        // Calculate total booked capacity from confirmed bookings
        return confirmedBookings.stream()
                .mapToInt(Booking::getNumberOfGuests)
                .sum();
    }

    public BookingDTO getBooking(Long id) {
        Booking booking = bookingRepository.findById(id);
        if (booking == null) {
            throw new BookingNotFoundException("Booking not found with id: " + id);
        }
        return convertToDTO(booking);
    }

    @Transactional(value = TxType.REQUIRED, rollbackOn = InsufficientCapacityException.class)
    public BookingDTO updateBooking(Long id, BookingDTO bookingDTO) {
        Booking existingBooking = bookingRepository.findById(id);
        if (existingBooking == null) {
            throw new BookingNotFoundException("Booking not found with id: " + id);
        }

        Hotel hotel = hotelRepository.findById(bookingDTO.getHotelId());
        if (hotel == null) {
            throw new IllegalArgumentException("Hotel not found with id: " + bookingDTO.getHotelId());
        }

        User user = userRepository.findById(bookingDTO.getUserId());
        if (user == null) {
            throw new IllegalArgumentException("User not found with id: " + bookingDTO.getUserId());
        }

        // Capacity Check Logic
        Hotel hotelForUpdate = hotelRepository.findByIdForUpdate(bookingDTO.getHotelId()); // Custom method with SELECT FOR UPDATE
        if (hotelForUpdate == null) {
            throw new HotelNotFoundException("Hotel not found with id: " + bookingDTO.getHotelId());
        }

        int bookedCapacity = getBookedCapacityForHotelAndDates(hotelForUpdate.getId(), bookingDTO.getCheckinDate(), bookingDTO.getCheckoutDate());
        int requestedCapacity = bookingDTO.getNumberOfGuests();
        int hotelCapacity = hotelForUpdate.getCapacity();
        int availableCapacity = hotelCapacity - bookedCapacity;

        if (requestedCapacity > availableCapacity) {
            throw new InsufficientCapacityException("Hotel has insufficient capacity for the requested number of guests.");
        }

        existingBooking.setHotel(hotel);
        existingBooking.setUser(user);
        existingBooking.setCheckinDate(bookingDTO.getCheckinDate());
        existingBooking.setCheckoutDate(bookingDTO.getCheckoutDate());
        existingBooking.setNumberOfGuests(bookingDTO.getNumberOfGuests());
        existingBooking.setTotalPrice(bookingDTO.getTotalPrice());

        bookingRepository.persist(existingBooking);
        return convertToDTO(existingBooking);
    }

    @Transactional
    public boolean deleteBooking(Long id) {
        Booking existingBooking = bookingRepository.findById(id);
        if (existingBooking == null) {
            return false;
        }
        existingBooking.setBookingStatus(BookingStatus.CANCELLED); // Update status to CANCELLED
        bookingRepository.persist(existingBooking); // Persist the updated status
        return true;
    }

    private BookingDTO convertToDTO(Booking booking) {
        BookingDTO dto = new BookingDTO();
        dto.setId(booking.getId());
        dto.setHotelId(booking.getHotel().getId());
        dto.setUserId(booking.getUser().getId());
        dto.setCheckinDate(booking.getCheckinDate());
        dto.setCheckoutDate(booking.getCheckoutDate());
        dto.setNumberOfGuests(booking.getNumberOfGuests());
        dto.setTotalPrice(booking.getTotalPrice());
        dto.setBookingStatus(booking.getBookingStatus());
        return dto;
    }

    private Booking convertToEntity(BookingDTO bookingDTO) {
        Booking booking = new Booking();
        booking.setCheckinDate(bookingDTO.getCheckinDate());
        booking.setCheckoutDate(bookingDTO.getCheckoutDate());
        booking.setNumberOfGuests(bookingDTO.getNumberOfGuests());
        booking.setTotalPrice(bookingDTO.getTotalPrice());
        booking.setBookingStatus(bookingDTO.getBookingStatus());
        return booking;
    }
}