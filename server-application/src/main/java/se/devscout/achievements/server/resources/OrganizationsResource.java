package se.devscout.achievements.server.resources;

import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import se.devscout.achievements.server.api.OrganizationBaseDTO;
import se.devscout.achievements.server.api.OrganizationDTO;
import se.devscout.achievements.server.data.dao.DaoException;
import se.devscout.achievements.server.data.dao.ObjectNotFoundException;
import se.devscout.achievements.server.data.dao.OrganizationsDao;
import se.devscout.achievements.server.data.model.Organization;
import se.devscout.achievements.server.data.model.OrganizationProperties;
import se.devscout.achievements.server.auth.User;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrganizationsResource extends AbstractResource {
    private OrganizationsDao dao;

    public OrganizationsResource(OrganizationsDao dao) {
        this.dao = dao;
    }

    @GET
    @Path("{organizationId}")
    @UnitOfWork
    public OrganizationBaseDTO get(@PathParam("organizationId") UuidString id, @Auth Optional<User> user) {
        try {
            final Organization organization = dao.read(id.asUUID());
            return user.isPresent() ? map(organization, OrganizationDTO.class) : map(organization, OrganizationBaseDTO.class);
        } catch (ObjectNotFoundException e) {
            throw new NotFoundException();
        }
    }

    @GET
    @UnitOfWork
    public List<OrganizationDTO> find(@QueryParam("filter") String filter, @Auth User user) {
        try {
            return dao.find(filter).stream().map(o -> map(o, OrganizationDTO.class)).collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @POST
    @UnitOfWork
    public Response create(OrganizationDTO input,
                           @Auth User user) {
        try {
            final Organization organization = dao.create(map(input, OrganizationProperties.class));
            final URI location = uriInfo.getRequestUriBuilder().path(organization.getId().toString()).build();
            return Response
                    .created(location)
                    .entity(map(organization, OrganizationDTO.class))
                    .build();
        } catch (DaoException e) {
            return Response.serverError().build();
        }
    }

    @PUT
    @UnitOfWork
    @Path("{organizationId}")
    public Response update(@PathParam("organizationId") UUID id,
                           OrganizationDTO input,
                           @Auth User user) {
        try {
            final Organization organization = dao.update(id, map(input, OrganizationProperties.class));
            return Response
                    .ok()
                    .entity(map(organization, OrganizationDTO.class))
                    .build();
        } catch (ObjectNotFoundException e) {
            throw new NotFoundException("Could not find " + id.toString());
        }
    }

    @DELETE
    @UnitOfWork
    @Path("{organizationId}")
    public Response delete(@PathParam("organizationId") UUID id,
                           @Auth User user) {
        try {
            dao.delete(id);
            return Response.noContent().build();
        } catch (ObjectNotFoundException e) {
            throw new NotFoundException();
        }
    }
}
