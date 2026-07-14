package br.com.termia.construajogue.compiler;

/** Problema encontrado pelo MapValidator. Erros bloqueiam o teste. */
public final class ValidationIssue {

    public static final int ERROR = 0;
    public static final int WARNING = 1;

    public final int severity;
    public final String code;
    public final String message;

    public ValidationIssue(int severity, String code, String message) {
        this.severity = severity;
        this.code = code;
        this.message = message;
    }

    public boolean isError() {
        return severity == ERROR;
    }

    @Override
    public String toString() {
        return (isError() ? "ERRO " : "AVISO ") + code + ": " + message;
    }
}
