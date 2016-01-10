package net.wasdev.gameon.concierge;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("health")
public class Health {

    /**
     * GET /concierge/health
     */
    @GET
    public Response healthCheck() {
            return Response.ok("All is well").build();
    }

}
