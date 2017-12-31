package se.devscout.achievements.server.resources.authenticator;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public class JwtAuthenticator implements Authenticator<String, User> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticator.class);
    private static final String ISSUER = "achievements";
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtAuthenticator(Algorithm algorithm) {
        this.algorithm = algorithm;
        verifier = JWT.require(this.algorithm).withIssuer(ISSUER).build();
    }

    @Override
    public Optional<User> authenticate(String token) throws AuthenticationException {
        try {
            final DecodedJWT jwt = verifier.verify(token);
            final User user = new User(
                    jwt.getClaim("id").asInt(),
                    UUID.fromString(jwt.getClaim("credentials").asString()),
                    jwt.getSubject()
            );
            return Optional.of(user);
        } catch (JWTVerificationException e) {
            LOGGER.error("Exception when trying to validate credentials", e);
            return Optional.empty();
        }
    }

    public String generateToken(String user, UUID credentialsId, Integer personId) {
        return JWT.create()
                .withSubject(user)
                .withIssuer(ISSUER)
                .withClaim("credentials", credentialsId.toString())
                .withClaim("id", personId)
                .sign(algorithm);
    }

}
