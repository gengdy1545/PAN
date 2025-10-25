package io.gengdy.pan.exception;

public class GeminiException extends Exception
{
    public GeminiException()
    {
        super();
    }

    public GeminiException(String message)
    {
        super(message);
    }

    public GeminiException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
