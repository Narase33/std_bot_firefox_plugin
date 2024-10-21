import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    private final HashMap<String, ArrayList<String>> pageCache = new HashMap<>();
    private long lastPageLoadTime = 0;
    private FileWriter logFile = null;

    public static void main(String[] args) {
        Main main = new Main();
        try {
            main.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void logToFile(String msg) {
        try {
            logFile.write(msg + "\n");
            logFile.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void log() {
        log("");
    }

    private void log(String msg) {
        System.out.println(msg);
        logToFile(msg);
    }

    private void logErr() {
        logErr("");
    }

    private void logErr(String msg) {
        System.err.println(msg);
        logToFile(msg);
    }

    private boolean anyOf(char c, String str) {
        return str.indexOf(c) >= 0;
    }

    private boolean isRealTokenName(String tokenName) {
        for (char c : tokenName.toCharArray()) {
            if (!(Character.isLetter(c) || Character.isDigit(c) || anyOf(c, "_:=<>[]()+-*/!"))) {
                return false;
            }
        }
        return true;
    }

    private ArrayList<String> loadPage(String urlAsString) throws IOException, PageNotFoundException, InterruptedException {
        final ArrayList<String> page = pageCache.get(urlAsString);
        if (page != null) {
            log("Found url in cache");
            return page;
        }


        final int maxTries = 10;
        for (int i = 0; i < maxTries; i++) {
            try {
                final long timeFromLastCall = System.currentTimeMillis() - lastPageLoadTime;
                if (timeFromLastCall < 5_000) {
                    Thread.sleep(5_000 - timeFromLastCall); // Please dont DOS cppreference.com
                }

                final URL url = new URL(urlAsString);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                connection.connect();
                lastPageLoadTime = System.currentTimeMillis();

                final int status = connection.getResponseCode();
                log("HTTP status code: " + status);

                if (status == 404) {
                    throw new PageNotFoundException();
                }

                final ArrayList<String> out = new ArrayList<>();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        out.add(inputLine);
                    }
                }

                pageCache.put(urlAsString, out);
                return out;
            } catch (IOException ignored) {

            }
        }
        logErr(MessageFormat.format("Couldnt reach {0} after {1} tries.", urlAsString, maxTries));
        System.exit(-1);
        return null;
    }

    private TokenLink findLinkInLine(String line, String namespace, String prefix, String postfix, boolean inContainer) {
        final String link_token_begin = "<a href=\"";
        final String link_token_end = "\"";

        int capsulationBegin_pos = line.indexOf(prefix);
        if (capsulationBegin_pos == -1) {
            return null;
        }

        String token_element = line;
        token_element = token_element.substring(capsulationBegin_pos + prefix.length());

        final int capsulationEnd_pos = token_element.indexOf(postfix);
        if (capsulationEnd_pos == -1) {
            return null;
        }

        token_element = token_element.substring(0, capsulationEnd_pos);

        String link = line;
        final int link_token_begin_pos = link.lastIndexOf(link_token_begin, capsulationBegin_pos);
        if (link_token_begin_pos == -1) {
            return null;
        }
        link = link.substring(link_token_begin_pos + link_token_begin.length());


        final int link_token_end_pos = link.indexOf(link_token_end);
        if (link_token_end_pos == -1) {
            return null;
        }
        link = link.substring(0, link_token_end_pos);

        if (link.startsWith("/mwiki")) { // Not yet associated with a site
            return null;
        }

        link = "https://en.cppreference.com" + link;
        String token = namespace + "::" + token_element;

        token = token.replaceAll("&lt;", "<");
        token = token.replaceAll("&gt;", ">");

        TokenType type = TokenType.Container;
        if (prefix.equals("<code>")) {
            type = TokenType.Namespace;
        } else if (token.endsWith("<>()")) {
            type = TokenType.Function;
            token = token.substring(0, token.length() - "<>()".length());
        } else if (token.endsWith("()")) {
            type = TokenType.Function;
            token = token.substring(0, token.length() - "()".length());
        } else if (inContainer) {
            type = TokenType.Function;
        }

        if (!isRealTokenName(token)) {
            logErr("Discarded invalid token name: " + token);
            return null;
        }

        return new TokenLink(token, link, type);
    }

    private TokenLink findLinkInLine(String line, String namespace, boolean inContainer) {
        TokenLink tokenLink = findLinkInLine(line, namespace, "<tt>", "</tt>", inContainer);
        if (tokenLink == null) {
            tokenLink = findLinkInLine(line, namespace, "<code>", "</code>", inContainer);
        }

        if (tokenLink == null) {
            tokenLink = findLinkInLine(line, namespace, "<span>", "</span>", inContainer);
        }

        return tokenLink;
    }

    private IndexResult findLinksInContainer(ArrayList<String> htmlPage, String namespace) {
        IndexResult result = new IndexResult();

        int i = 0;
        while ((i < htmlPage.size()) && !htmlPage.get(i).contains("id=\"Member_functions\">Member functions</span></h3>")) {
            i++;
        }

        for (; i < htmlPage.size(); i++) {
            String line = htmlPage.get(i);
            if (line.contains("id=\"See_also\">See also</span></h3>")) {
                break; // We end if we encounter a "See also" section
            }

            final TokenLink tokenLink = findLinkInLine(line, namespace, true);
            if (tokenLink != null) {
                switch (tokenLink.type) {
                    case Function:
                        log("Found: " + tokenLink);
                        result.functions.add(tokenLink);
                        break;
                    case Container:
                        log("Found: " + tokenLink);
                        result.containers.add(tokenLink);
                        break;
                    case Namespace:
                        logErr("Discarded namespace in container: " + tokenLink);
                        break;
                }
            }
        }

        return result;
    }

    private IndexResult findLinksInSymbolIndex(ArrayList<String> htmlPage, String namespace) {
        IndexResult result = new IndexResult();

        int i = 0;
        while ((i < htmlPage.size()) && !htmlPage.get(i).contains("This page tries to list all the symbols ")) {
            i++;
        }

        for (; i < htmlPage.size(); i++) {
            String line = htmlPage.get(i);
            if (line.contains("id=\"See_also\">See also</span></h3>")) {
                break; // We end if we encounter a "See also" section
            }

            final TokenLink tokenLink = findLinkInLine(line, namespace, false);
            if (tokenLink != null) {
                switch (tokenLink.type) {
                    case Function:
                        log("Found: " + tokenLink);
                        result.functions.add(tokenLink);
                        break;
                    case Container:
                        log("Found: " + tokenLink);
                        result.containers.add(tokenLink);
                        break;
                    case Namespace:
                        log("Found: " + tokenLink);
                        result.namespaces.add(tokenLink);
                        break;
                }
            }
        }

        return result;
    }

    private ArrayList<TokenLink> indexNamespace(String url, String namespace) throws InterruptedException {
        log(MessageFormat.format("\nIndexing: {0} because of namespace {1}", url, namespace));

        final ArrayList<TokenLink> result = new ArrayList<>();

        try {
            final ArrayList<String> page = loadPage(url);
            final IndexResult indexResult = findLinksInSymbolIndex(page, namespace);

            result.addAll(indexResult.functions);

            for (TokenLink tokenLink : indexResult.containers) {
                final ArrayList<TokenLink> containerResult = indexContainer(tokenLink.link, tokenLink.token);
                result.addAll(containerResult);
                result.add(tokenLink);
            }

            for (TokenLink tokenLink : indexResult.namespaces) {
                final ArrayList<TokenLink> namespaceResult = indexNamespace(tokenLink.link, tokenLink.token);
                result.addAll(namespaceResult);
            }
        } catch (IOException | PageNotFoundException e) {
            logErr(MessageFormat.format("Site not found: {0}", url));
        }

        return result;
    }

    private ArrayList<TokenLink> indexContainer(String url, String namespace) throws InterruptedException {
        log(MessageFormat.format("\nIndexing: {0} because of container {1}", url, namespace));

        final ArrayList<TokenLink> result = new ArrayList<>();

        try {
            final ArrayList<String> page = loadPage(url);
            final IndexResult indexResult = findLinksInContainer(page, namespace);

            result.addAll(indexResult.functions);
        } catch (IOException | PageNotFoundException e) {
            logErr(MessageFormat.format("Site not found: {0}", url));
        }

        return result;
    }

    private ArrayList<TokenLink> indexCppReference() throws InterruptedException {
        return indexNamespace("https://en.cppreference.com/w/cpp/symbol_index", "std");
    }

    void removeDuplicates(ArrayList<TokenLink> tokenLinks) {
        for (int i = 0; i < tokenLinks.size(); i++) {
            boolean found = false;
            for (int j = i + 1; j < tokenLinks.size(); j++) {
                if (tokenLinks.get(i).token.equals(tokenLinks.get(j).token)) {
                    logErr("Removing duplicate: " + tokenLinks.get(j));
                    tokenLinks.remove(j);
                    found = true;
                    j--;
                }
            }

            if (found) {
                logErr("Removing duplicate: " + tokenLinks.get(i));
                tokenLinks.remove(i);
                i--;
            }
        }
    }

    void writeJSON(ArrayList<TokenLink> tokenLinks) {
        StringBuilder json = new StringBuilder();
        json.append("const stlData = {");
        for (TokenLink tokenLink : tokenLinks) {
            final String tokenToWrite = tokenLink.token.substring("std::".length());
            final String linkToWrite = tokenLink.link.substring("https://en.cppreference.com/w/cpp/".length());
            json.append(MessageFormat.format("\n\t\"{0}\": \"{1}\",", tokenToWrite, linkToWrite));
        }
        json.deleteCharAt(json.length() - 1);
        json.append("\n};");

        try (FileWriter out = new FileWriter("data.json")) {
            out.write(json.toString());
        } catch (IOException e) {
            logErr(e.getMessage());
            System.out.println(json);
        }
    }

    private void start() throws InterruptedException, IOException {
        logFile = new FileWriter("log.txt");

        final long start = System.currentTimeMillis();

        //final ArrayList<TokenLink> tokens = indexNamespace("https://en.cppreference.com/w/cpp/symbol_index/chrono", "std::chrono");
        //final ArrayList<TokenLink> tokens = indexContainer("https://en.cppreference.com/w/cpp/container/vector", "std::vector");
        final ArrayList<TokenLink> tokens = indexCppReference();

        log();
        tokens.forEach(System.out::println);
        System.out.printf("%d Token entries\n", tokens.size());

        tokens.sort(Comparator.comparing(o -> o.token));
        removeDuplicates(tokens);
        writeJSON(tokens);
        final long end = System.currentTimeMillis();

        log("Finished!");
        final long duration_ms = end - start;
        log(MessageFormat.format("Time taken: {0} ms", duration_ms));
        log(MessageFormat.format("Time taken: {0} s", duration_ms / 1000.0));
        log(MessageFormat.format("Time taken: {0} min", duration_ms / (60 * 1000.0)));
    }

    enum TokenType {
        Function, Container, Namespace
    }

    private static class TokenLink {
        public String token;
        public String link;
        public TokenType type;

        public TokenLink(String token, String link, TokenType type) {
            this.token = token;
            this.link = link;
            this.type = type;
        }

        public String toString() {
            return MessageFormat.format("({0}) {1} -> {2}", type.toString(), token, link);
        }
    }

    private static class IndexResult {
        public ArrayList<TokenLink> containers = new ArrayList<>();
        public ArrayList<TokenLink> functions = new ArrayList<>();
        public ArrayList<TokenLink> namespaces = new ArrayList<>();
    }

    private static class PageNotFoundException extends Exception {
        public PageNotFoundException() {
        }
    }
}