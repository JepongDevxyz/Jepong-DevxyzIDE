package com.jepongdevxyz.idebuild.core.process;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable process launch specification used by DevxyzIDE tools. */
public final class ProcessRequest {
    private final List<String> command;
    private final File workingDirectory;
    private final Map<String, String> environment;
    private final boolean mergeErrorStream;
    private final List<String> secretsToRedact;

    public ProcessRequest(List<String> command,
                          File workingDirectory,
                          Map<String, String> environment,
                          boolean mergeErrorStream,
                          List<String> secretsToRedact) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("command must not be empty");
        ArrayList<String> commandCopy = new ArrayList<String>();
        for (String part : command) {
            if (part == null) throw new IllegalArgumentException("command part must not be null");
            commandCopy.add(part);
        }
        this.command = Collections.unmodifiableList(commandCopy);
        this.workingDirectory = workingDirectory;
        this.environment = Collections.unmodifiableMap(
                environment == null ? new HashMap<String, String>() : new HashMap<String, String>(environment));
        this.mergeErrorStream = mergeErrorStream;

        ArrayList<String> redactions = new ArrayList<String>();
        if (secretsToRedact != null) {
            for (String secret : secretsToRedact) {
                if (secret != null && secret.length() > 0) redactions.add(secret);
            }
        }
        this.secretsToRedact = Collections.unmodifiableList(redactions);
    }

    public List<String> getCommand() { return command; }
    public File getWorkingDirectory() { return workingDirectory; }
    public Map<String, String> getEnvironment() { return environment; }
    public boolean isMergeErrorStream() { return mergeErrorStream; }
    public List<String> getSecretsToRedact() { return secretsToRedact; }
}
