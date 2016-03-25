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

import net.wasdev.gameon.room.common.Room;

public class Connection {

	private Room startRoom;
	private Room endRoom;
	private String startingEntrance;
	public Connection(Room startingRoom, Room endRoom, String startingEntrance) {
		this.startingEntrance = startingEntrance;
		this.startRoom = startingRoom;
		this.endRoom = endRoom;
	}
	public Room getStartRoom() {
		return startRoom;
	}

	public Room getEndRoom() {
		return endRoom;
	}

	public String getStartingEntrance() {
		return startingEntrance;
	}

}
