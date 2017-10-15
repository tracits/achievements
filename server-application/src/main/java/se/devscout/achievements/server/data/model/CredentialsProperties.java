package se.devscout.achievements.server.data.model;

import se.devscout.achievements.server.auth.PasswordValidator;
import se.devscout.achievements.server.auth.SecretValidator;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.Size;

@MappedSuperclass
public class CredentialsProperties {
    private String username;
    @Basic
    @Column(length = 1024)
    @Size(max = 1024)
    private byte[] secret;

    private IdentityProvider provider;

    public CredentialsProperties() {
    }

    public CredentialsProperties(String username, SecretValidator validator) {
        this.username = username;
        this.secret = validator.getSecret();
        if (validator instanceof PasswordValidator) {
            provider = IdentityProvider.PASSWORD;
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public byte[] getSecret() {
        return secret;
    }

    public void setSecret(byte[] secret) {
        this.secret = secret;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public void setProvider(IdentityProvider provider) {
        this.provider = provider;
    }

    public SecretValidator getSecretValidator() {
        switch (provider) {
            case PASSWORD:
                return new PasswordValidator(secret);
        }
        throw new IllegalArgumentException("Cannot handle identity provider " + provider);
    }
}