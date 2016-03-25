/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package net.wasdev.gameon.concierge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;

import net.wasdev.gameon.room.common.RegistrationResponse;
import net.wasdev.gameon.room.common.Room;
import net.wasdev.gameon.room.common.RoomToEndpoints;

@ApplicationScoped
public class Concierge {
	Map<String, RoomToEndpoints> roomDirectory = new HashMap<String, RoomToEndpoints>();
	Set<String> startingRooms = new HashSet<String>();
	int starterRoomIndex = 0;

	PlacementStrategy ps = new ManualWiringPlacement();

	public Concierge(PlacementStrategy placementStrategy) {
		ps = placementStrategy;
	}

	public Concierge() {
		System.out.println("CONCIERGE IS STARTING: " + this.hashCode());
		ps = new ManualWiringPlacement();
	}

	public RoomToEndpoints getStartingRoom() {
		boolean first = true;
		while(first){
			int roomIndex = 0;
			
			if (starterRoomIndex == 0){
				first = false;
			}			
			for (String roomId : startingRooms) {
				RoomToEndpoints roomCollection = roomDirectory.get(roomId);
				if (roomCollection != null) {
					roomIndex++;
					if (roomIndex > starterRoomIndex){
						System.out.println("Request for starting room : \n" + roomCollection + "\n" + roomDirectory);
						starterRoomIndex = roomIndex;
						return roomCollection;
					}
				}
			}
			starterRoomIndex = 0;
		}
		System.out.println("Request for starting room : \n null\n" + roomDirectory);
		return null;
	}

	public RoomToEndpoints exitRoom(String currentRoomId, String exitName) {
		String roomId = ps.getConnectingRooms(currentRoomId, exitName);
		return roomDirectory.get(roomId);
	}

	public RegistrationResponse registerRoom(Room room, String ownerId) {
		boolean reRegistration = roomDirectory.containsKey(room.getRoomName());
		
		if(!reRegistration)System.out.println("Processing registration by '"+ownerId+"' for : \n" + room.toString());
		
		RoomToEndpoints rte = roomDirectory.get(room.getRoomName());
		if (rte == null) {
			rte = new RoomToEndpoints();
			rte.setRoomId(room.getRoomName());
		}

		List<String> endpoints = rte.getEndpoints();
		endpoints.add(room.getAttribute("endPoint"));
		roomDirectory.put(room.getRoomName(), rte);
		boolean startLocation = true;
		String setStartLocation = room.getAttribute("startLocation");
		if (setStartLocation != null) {
			startLocation = Boolean.valueOf(setStartLocation);
		}

		ps.placeRoom(room);
		if (startLocation) {
			startingRooms.add(room.getRoomName());
		}
		RegistrationResponse rr = new RegistrationResponse();
		return rr;
	}

	public RoomToEndpoints getRoom(String roomId) {
		return roomDirectory.get(roomId);
	}
}
