package com.hrs.api_gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hrs.api_gateway.entity.Hotel;
import com.hrs.api_gateway.exception.HotelNotFoundException;
import com.hrs.api_gateway.model.HotelDTO;
import com.hrs.api_gateway.repository.HotelRepository;
import com.hrs.api_gateway.utils.CacheKey;
import io.quarkus.redis.client.RedisClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class HotelService {

    private static final Logger LOG = Logger.getLogger(HotelService.class);
    private static final Duration HOTEL_CACHE_EXPIRATION = Duration.ofHours(1); // Cache hotel data for 1 hour
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String HOTEL_INDEX = "connect.hrs_booking.hotels";
    @Inject
    HotelRepository hotelRepository;
    @Inject
    RedisClient redisClient;
    @Inject
    RestClient restClient;

    public List<HotelDTO> searchHotels(String query) throws IOException {
        // Construct Elasticsearch query JSON payload manually
        String searchQueryJson = buildSearchQueryJson(query);
        HttpEntity entity = new NStringEntity(searchQueryJson, ContentType.APPLICATION_JSON);

        Request request = new Request("GET", "/" + HOTEL_INDEX + "/_search"); // Index: "hotel", Endpoint: _search
        request.setEntity(entity);

        Response response = restClient.performRequest(request);

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IOException("Elasticsearch search failed: " + response.getStatusLine());
        }

        String responseBody = org.apache.http.util.EntityUtils.toString(response.getEntity());
        com.fasterxml.jackson.databind.JsonNode jsonResponse = objectMapper.readTree(responseBody);

        return mapRestClientResponseToHotelDTOs(jsonResponse); // Map from JSON response
    }

    private String buildSearchQueryJson(String query) throws IOException {
        ObjectNode boolQueryNode = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode shouldClauses = objectMapper.createArrayNode(); // ArrayNode for SHOULD clauses

        // Split query into keywords
        String[] keywords = query.toLowerCase().split("\\s+"); // Split by whitespace, lowercase

        // Add a SHOULD clause for each keyword and each field (name, city, address)
        for (String keyword : keywords) {
            ObjectNode matchNameNode = objectMapper.createObjectNode();
            matchNameNode.put("name", keyword);
            shouldClauses.add(objectMapper.createObjectNode().set("match", matchNameNode)); // Match in name

            ObjectNode matchCityNode = objectMapper.createObjectNode();
            matchCityNode.put("city", keyword);
            shouldClauses.add(objectMapper.createObjectNode().set("match", matchCityNode)); // Match in city

            ObjectNode matchAddressNode = objectMapper.createObjectNode();
            matchAddressNode.put("address", keyword);
            shouldClauses.add(objectMapper.createObjectNode().set("match", matchAddressNode)); // Match in address
        }

        boolQueryNode.set("should", shouldClauses); // Set SHOULD clauses in bool query
        boolQueryNode.put("minimum_should_match", 1); // At least one SHOULD clause must match

        ObjectNode boolQueryWrapper = objectMapper.createObjectNode(); // Wrap bool query in a query node
        boolQueryWrapper.set("bool", boolQueryNode);

        ObjectNode rootNode = objectMapper.createObjectNode(); // Root query node
        rootNode.set("query", boolQueryWrapper);

        return objectMapper.writeValueAsString(rootNode);
    }

    private List<HotelDTO> mapRestClientResponseToHotelDTOs(com.fasterxml.jackson.databind.JsonNode jsonResponse) {
        List<HotelDTO> hotelDTOs = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode hitsNode = jsonResponse.path("hits").path("hits");
        if (hitsNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode hit : hitsNode) {
                try {
                    Hotel hotel = objectMapper.readValue(hit.path("_source").toString(), Hotel.class);
                    hotelDTOs.add(mapToDto(hotel));
                } catch (IOException e) {
                    e.printStackTrace(); // Handle JSON parsing error
                }
            }
        }
        return hotelDTOs;
    }


    @Transactional
    public HotelDTO createHotel(HotelDTO hotelDTO) throws IOException {
        Hotel hotel = mapToEntity(hotelDTO);
        hotelRepository.persist(hotel);
        return mapToDto(hotel);
    }

    public HotelDTO getHotel(Long id) {
        String cacheKey = CacheKey.HOTEL_BY_ID.getKey(id);

        // 1. Check Cache First
        if (redisClient.get(cacheKey) != null) {
            String cachedHotelJson = redisClient.get(cacheKey).toString();
            if (cachedHotelJson != null) {
                LOG.debugf("Cache hit for hotel ID: %s", id);
                try {
                    return objectMapper.readValue(cachedHotelJson, HotelDTO.class); // Deserialize from cache
                } catch (IOException e) {
                    LOG.warn("Error deserializing hotel from cache", e); // Log error, but fall back to DB
                }
            }
        }

        // 2. If Cache Miss, Retrieve from Database
        Hotel hotel = hotelRepository.findById(id);
        if (hotel == null) {
            throw new HotelNotFoundException("Hotel not found with id: " + id);
        }
        HotelDTO hotelDTO = mapToDto(hotel);

        // 3. Store in Cache
        try {
            redisClient.setex(cacheKey, String.valueOf(HOTEL_CACHE_EXPIRATION.getSeconds()), objectMapper.writeValueAsString(hotelDTO)); // Serialize to JSON and cache
            LOG.debugf("Cache miss, stored hotel ID: %s in cache", id);
        } catch (IOException e) {
            LOG.warn("Error serializing hotel to cache", e); // Log cache serialization error
        }

        // 4. Return HotelDTO
        return hotelDTO;
    }

    @Transactional
    public HotelDTO updateHotel(Long id, HotelDTO hotelDTO) throws IOException {
        Hotel existingHotel = hotelRepository.findById(id);
        if (existingHotel == null) {
            throw new HotelNotFoundException("Hotel not found with id: " + id);
        }
        existingHotel.setName(hotelDTO.getName());
        existingHotel.setCity(hotelDTO.getCity());
        existingHotel.setAddress(hotelDTO.getAddress());
        existingHotel.setCapacity(hotelDTO.getCapacity());
        hotelRepository.persist(existingHotel);
        invalidateHotelCache(id);

        return mapToDto(existingHotel);
    }

    @Transactional
    public boolean deleteHotel(Long id) {
        Hotel existingHotel = hotelRepository.findById(id);
        if (existingHotel == null) {
            return false;
        }
        boolean isDeleted = hotelRepository.deleteById(id);
        if (isDeleted) {
            invalidateHotelCache(id);
        }
        return true;
    }

    private void invalidateHotelCache(Long hotelId) {
        String cacheKey = CacheKey.HOTEL_BY_ID.getKey(hotelId);
        redisClient.del(List.of(cacheKey)); // Remove hotel data from Redis cache
        LOG.debugf("Invalidated cache for hotel ID: %s", hotelId);
    }

    public HotelDTO mapToDto(Hotel hotel) {
        return new HotelDTO(hotel.id, hotel.name, hotel.city, hotel.address, hotel.capacity);
    }

    private Hotel mapToEntity(HotelDTO hotelDTO) {
        Hotel hotel = new Hotel();
        hotel.id = hotelDTO.getId();
        hotel.name = hotelDTO.getName();
        hotel.city = hotelDTO.getCity();
        hotel.address = hotelDTO.getAddress();
        hotel.capacity = hotelDTO.getCapacity();
        return hotel;
    }
}