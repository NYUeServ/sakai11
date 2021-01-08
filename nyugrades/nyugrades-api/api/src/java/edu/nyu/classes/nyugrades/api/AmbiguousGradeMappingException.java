package edu.nyu.classes.nyugrades.api;

public class AmbiguousGradeMappingException extends Exception
{
    public AmbiguousGradeMappingException(String message) {
        super(message);
    }

    public AmbiguousGradeMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
