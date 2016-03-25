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
import java.util.List;
import java.util.Map;

import net.wasdev.gameon.room.common.Exit;
import net.wasdev.gameon.room.common.Room;

public class ManualWiringPlacement implements PlacementStrategy {
	
	Map<String, List<Exit>> exitMap = new HashMap<String, List<Exit>>(); 
	
	@Override
	public String getConnectingRooms(String currentRoom, String exitName) {
		String roomName = null;
		List<Exit> currentRoomExits = exitMap.get(currentRoom);
		if (currentRoomExits != null) {
			for (Exit exit : currentRoomExits) {
				if (exit.getName().equals(exitName)) {
					roomName = exit.getRoom();
				}
			}
		}
		return roomName;
	}

	@Override
	public void placeRoom(Room room) {
		exitMap.put(room.getRoomName(), room.getExits());
	}

}
