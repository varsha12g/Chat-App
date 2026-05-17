package com.chatapp.room;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.chatapp.user.User;
import com.chatapp.user.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public RoomController(RoomRepository roomRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<RoomResponse> myRooms(@AuthenticationPrincipal User user) {
        return roomRepository.findByMemberIdsContainingOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(RoomResponse::from)
                .toList();
    }

    @PostMapping("/join")
    public RoomResponse joinRoom(@AuthenticationPrincipal User user, @Valid @RequestBody JoinRoomRequest request) {
        String requestedRoomId = Objects.requireNonNull(request.roomId());
        String roomId = normalizeRoomId(requestedRoomId);
        Room room = roomRepository.findById(roomId).orElseGet(() -> {
            Room nextRoom = new Room();
            nextRoom.setId(roomId);
            nextRoom.setName(roomId);
            nextRoom.setType(RoomType.GROUP);
            nextRoom.setMemberIds(new ArrayList<>());
            return nextRoom;
        });

        if (!room.getMemberIds().contains(user.getId())) {
            List<String> members = new ArrayList<>(room.getMemberIds());
            members.add(user.getId());
            room.setMemberIds(members);
        }

        return RoomResponse.from(roomRepository.save(room));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@AuthenticationPrincipal User user, @Valid @RequestBody CreateRoomRequest request) {
        List<String> members = new java.util.ArrayList<>(request.memberIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .sorted()
                .toList());

        if (!members.contains(user.getId())) {
            members.add(user.getId());
            members.sort(Comparator.naturalOrder());
        }

        if (members.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A room needs at least two members");
        }

        for (String member : members) {
            String memberId = Objects.requireNonNull(member);
            if (!userRepository.existsById(memberId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown member id: " + memberId);
            }
        }

        RoomType type = request.type() == null ? RoomType.GROUP : request.type();
        if (type == RoomType.DIRECT) {
            List<String> roomMembers = List.copyOf(members);
            Optional<Room> existing = roomRepository.findByMemberIdsContainingOrderByCreatedAtDesc(user.getId())
                    .stream()
                    .filter(room -> room.getType() == RoomType.DIRECT)
                    .filter(room -> room.getMemberIds().size() == roomMembers.size())
                    .filter(room -> room.getMemberIds().containsAll(roomMembers))
                    .findFirst();
            if (existing.isPresent()) {
                return RoomResponse.from(existing.get());
            }
        }

        Room room = new Room();
        room.setName(request.name());
        room.setType(type);
        room.setMemberIds(members);
        return RoomResponse.from(roomRepository.save(room));
    }

    private @NonNull String normalizeRoomId(@NonNull String roomId) {
        String normalized = roomId.trim().toLowerCase().replaceAll("\\s+", "-");
        if (!normalized.matches("[a-z0-9][a-z0-9-_]{2,39}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Room ID must be 3-40 characters and use letters, numbers, hyphen, or underscore"
            );
        }
        return normalized;
    }

    public record JoinRoomRequest(
            @NonNull @NotBlank @Size(min = 3, max = 40) String roomId
    ) {
    }

    public record CreateRoomRequest(
            @NotBlank String name,
            RoomType type,
            @NotEmpty List<String> memberIds
    ) {
    }

    public record RoomResponse(String id, String name, RoomType type, List<String> memberIds) {
        static RoomResponse from(Room room) {
            return new RoomResponse(room.getId(), room.getName(), room.getType(), room.getMemberIds());
        }
    }
}
