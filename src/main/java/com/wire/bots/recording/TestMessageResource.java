package com.wire.bots.recording;

import com.codahale.metrics.annotation.Metered;
import com.wire.xenon.backend.models.ErrorMessage;
import com.wire.xenon.tools.Logger;
import io.swagger.annotations.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Api
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/bots/{bot}/messages")
public class TestMessageResource {

    @POST
    @ApiOperation(value = "New OTR Message")
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Invalid Authorization", response = ErrorMessage.class),
            @ApiResponse(code = 503, message = "Missing bot's state object", response = ErrorMessage.class),
            @ApiResponse(code = 200, message = "Alles gute")})
    @Authorization("Bearer")
    @Metered
    public Response newMessage(@ApiParam("UUID Bot instance id") @PathParam("bot") UUID botId,
                               @ApiParam String payload) {


        Logger.info("TestMessageResource: %s", payload);

        return Response.
                ok().
                status(200).
                build();
    }
}


