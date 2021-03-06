package se.devscout.achievements.server.auth.jwt;

import java.util.Set;
import java.util.UUID;

public class JwtSignInToken {
    private final String personName;
    private final int personId;
    private final UUID credentialsId;
    private final Set<String> roles;
    private final UUID organizationId;

    public JwtSignInToken(String personName, int personId, UUID credentialsId, Set<String> roles, UUID organizationId) {
        this.personName = personName;
        this.personId = personId;
        this.credentialsId = credentialsId;
        this.roles = roles;
        this.organizationId = organizationId;
    }

    public String getPersonName() {
        return personName;
    }

    public int getPersonId() {
        return personId;
    }

    public UUID getCredentialsId() {
        return credentialsId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }
}
