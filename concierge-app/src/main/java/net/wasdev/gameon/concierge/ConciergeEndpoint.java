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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.wasdev.gameon.room.common.Room;
import net.wasdev.gameon.room.common.RoomToEndpoints;
import net.wasdev.gameon.room.common.RoomToEndpointsWrapper;

@Path("/")
public class ConciergeEndpoint {

    @Context
    HttpServletRequest httpRequest;
	
	@Inject
	Concierge c;

	@GET
	@Path("startingRoom")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getStartingRoom() {
		RoomToEndpoints startingRoom = c.getStartingRoom();

		if ( startingRoom == null )
			return Response.status(404).build();

		RoomToEndpointsWrapper ew = new RoomToEndpointsWrapper();
		ew.setRel(startingRoom);
		return Response.ok(ew).build();
	}

	@GET
	@Path("rooms/{roomId}/{exitName}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response exitRoom(@PathParam("roomId") String roomId, @PathParam("exitName") String exitName) {
		RoomToEndpoints ec = c.exitRoom(roomId, exitName);

		if ( ec.getEndpoints().isEmpty() )
			return Response.status(404).build();

		RoomToEndpointsWrapper ew = new RoomToEndpointsWrapper();
		ew.setRel(ec);
		return Response.ok(ew).build();
	}

	@GET
	@Path("rooms/{roomId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getARoom(@PathParam("roomId") String roomId) {
		RoomToEndpointsWrapper ew = new RoomToEndpointsWrapper();
		ew.setRel(c.getRoom(roomId));
		return Response.ok(ew).build();
	}



	@POST
	@Path("registerRoom")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response registerRoom(Room room) {
        // set by the auth filter.
        String authId = (String) httpRequest.getAttribute("player.id");
        if(authId==null){
        	authId = "GameOn!";
        }
		return Response.ok(c.registerRoom(room,authId)).build();
	}


}
