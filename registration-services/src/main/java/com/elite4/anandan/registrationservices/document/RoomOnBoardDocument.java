package com.elite4.anandan.registrationservices.document;

import com.elite4.anandan.registrationservices.dto.Room;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "roomOnBoard")
public class RoomOnBoardDocument {

    @Id
    private String id;
    private Set<Room> rooms;
}
