package rw.smart.ecommerce.utils.exceptions.handler;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import rw.smart.ecommerce.utils.exceptions.DocumentStoreException;
import rw.smart.ecommerce.utils.exceptions.DuplicateResourceException;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.exceptions.ResourceNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The GraphQL counterpart to {@code GlobalExceptionHandler}.
 *
 * {@code @RestControllerAdvice} does not apply to GraphQL: there is no HTTP
 * status to set, since a GraphQL response is 200 with a populated
 * {@code errors} array. Without this resolver every domain exception would
 * surface as the opaque "INTERNAL_ERROR" that graphql-java falls back to.
 */
@Slf4j
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        ErrorType errorType = classify(exception);

        if (errorType == null) {
            log.error("Unhandled GraphQL exception", exception);
            return error(environment, ErrorType.INTERNAL_ERROR,
                    "An unexpected error occurred. Please try again later.", Map.of());
        }

        return error(environment, errorType, describe(exception), details(exception));
    }

    /** Null means "not one of ours" — the caller then hides the detail. */
    private ErrorType classify(Throwable exception) {
        if (exception instanceof ResourceNotFoundException) return ErrorType.NOT_FOUND;
        if (exception instanceof DuplicateResourceException) return ErrorType.BAD_REQUEST;
        if (exception instanceof InsufficientStockException) return ErrorType.BAD_REQUEST;
        if (exception instanceof InvalidInputException) return ErrorType.BAD_REQUEST;
        if (exception instanceof ConstraintViolationException) return ErrorType.BAD_REQUEST;
        // Without this, a mutation refused by @PreAuthorize would come back as
        // INTERNAL_ERROR — indistinguishable from a crash, and impossible for a
        // client to act on.
        if (exception instanceof AccessDeniedException) return ErrorType.FORBIDDEN;
        // And this one, for the case above's near neighbour. Method security
        // throws AccessDeniedException when a signed-in caller lacks the role, but
        // AuthenticationCredentialsNotFoundException - an AuthenticationException -
        // when there is no principal at all. Without this branch the second case
        // fell through to INTERNAL_ERROR, so "you need to sign in" reached the
        // client as "we crashed": a caller could not tell a fixable problem from
        // an unfixable one. GlobalExceptionHandler has always answered 401 here;
        // the GraphQL side had not.
        if (exception instanceof AuthenticationException) return ErrorType.UNAUTHORIZED;
        if (exception instanceof DocumentStoreException) return ErrorType.INTERNAL_ERROR;
        return null;
    }

    /**
     * A raw {@code ConstraintViolationException} message reads
     * "addReview.input.rating: Rating must be..." — the method path is noise to
     * a client, so the count is summarised and the field detail moved into
     * {@code extensions} where a form can act on it.
     *
     * A denial keeps a generic reason for the opposite purpose: naming what was
     * refused would let a caller map out the schema by probing it.
     */
    private String describe(Throwable exception) {
        if (exception instanceof AuthenticationException)
            return "Authentication required.";

        if (exception instanceof AccessDeniedException)
            return "You do not have permission to perform this action.";

        if (exception instanceof ConstraintViolationException violation) {
            int count = violation.getConstraintViolations().size();
            return "Validation failed for " + count + " field(s)";
        }
        return exception.getMessage();
    }

    private Map<String, Object> details(Throwable exception) {
        if (!(exception instanceof ConstraintViolationException violation)) return Map.of();

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        violation.getConstraintViolations().forEach(constraint ->
                fieldErrors.putIfAbsent(leafField(constraint.getPropertyPath().toString()), constraint.getMessage()));

        return Map.of("fieldErrors", fieldErrors);
    }

    /** "addReview.input.rating" -> "rating". */
    private String leafField(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    private GraphQLError error(DataFetchingEnvironment environment, ErrorType errorType,
                               String message, Map<String, Object> extensions) {

        return GraphQLError.newError()
                .errorType(errorType)
                .message(message)
                .path(environment.getExecutionStepInfo().getPath())
                .location(environment.getField().getSourceLocation())
                .extensions(extensions)
                .build();
    }
}
