package fr.bloomenetwork.fatestaynight.packager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FinalContainer<T> {

    private T value;

    FinalContainer(T value) {
        this.value = value;
    }
    T get() {
        return this.value;
    }
    void set(T value) {
        this.value = value;
    }
}
class ErrorDetectPattern {

    public final String msg;
    public final Pattern regex;

    ErrorDetectPattern(String msg, String regex) {
        this.msg = msg;
        this.regex = Pattern.compile(regex);
    }

    Matcher matcher(String line) {
        return this.regex.matcher(line);
    }

    static List<ErrorDetectPattern> fromArray(String[][] pairs) {
        List<ErrorDetectPattern> result = new ArrayList<>();
        for (String[] pair : pairs) {
            result.add(new ErrorDetectPattern(pair[0], pair[1]));
        }
        return result;
    }
}

public class TextProcess {

    //static private final Pattern talkerRegex = Pattern.compile("@say storage=[^\\W_]+_([^\\W_]+)_\\d+");
    static private final Pattern leftBracketRegex = Pattern.compile("(?<!\\\\)\\[");
    static private final Pattern rightBracketRegex = Pattern.compile("(?<!\\\\)\\]");
    static private final Pattern leftFrenchQuoteRegex = Pattern.compile("«");
    static private final Pattern rightFrenchQuoteRegex = Pattern.compile("»");
    static private final Pattern apostropheRegex = Pattern.compile("(?<=[A-Za-zÀ-ÿ])(?=')(?=(?!’))(?=(?!‘)).(?=[A-Za-zÀ-ÿŒœ])");

    static private final Pattern missingNbspRegex = Pattern.compile(
        "((?<=«) )|"+ // espace précédé de «
        "( (?=»|:|;|\\?|!))"); // ou espace suivi de », :, ;, ? ou !

    static private final List<ErrorDetectPattern> errorPatterns = ErrorDetectPattern.fromArray(new String[][]{
        {"problème de ponctuation",
            "(!\\?)|"+                      // devrait être '?!'
            "(\\.\\s*…)|(…\\s*\\.)|"+       // doit être remplacé soit par '.', soit par '…'
            "(…\\w)|"+                      // Il doit y avoir un espace après '…' avant une lettre
            "(\\[line\\d+\\]\\.)|"+         // la ligne est équivalent à un point
            "(…[?!])"                       // manque espace insécable avant '?'/'!'
        }, {"mauvais usage",
            "([Ss]imilaires?\\s*((à)|(au)))|"+ // A semblable à B / A et B sont similaires
            "((\\W\\s+|^)[Dd]u coup)|"+     // uniquement si conséquence immédiate
            "([Aa]u final)|"+               // uniquement pour le final d'une représentation
            "([Pp]allier\\s+((à)|(au)))"    // uniquement pour le final d'une représentation
        }, {"phrase non terminée",
            "(?<!(\\.|“|!|\\?|…|—))"+       // dialogue non terminé si ne termine pas par ., (rien), !, ?, … , — (cardatin)
            "(?<!\\[line\\d\\])"+           // [lineX],
            "(?<!\\[line\\d{2}\\])"+        // ou [lineXX]
            "”"                             // avant le guillemet fermant
        }, {"mauvaise orthographe",
            "\\b("+
            "(Sabre)|"+                     // -> Saber
            "(Bellerophon)|"+               // -> Bellérophon
            "(Ga[eé] Bolg)|"+               // -> Gáe Bolg
            "(Bedivere)|"+                  // -> Bédivère
            "(Cu\\s?chulain)|"+             // -> Cú Chulainn
            "(Hassan Sabbah)|"+             // -> Hasan-i Sabbâh
            "(Hercule)|"+                   // -> Héraclès
            "(Héraklês)|"+                  // -> Héraclès
            "(Kojiro)|"+                    // -> Kojirō
            "(Mato)|"+                      // -> Matō
            "(Medea)|"+                     // -> Médée
            "(Maeve)|"+                     // -> Medbe
            "(Perseus)|"+                   // -> Persée
            "(Ryuu?do)|"+                   // -> Ryūdō
            "(Shiro)|"+                     // -> Shirō
            "(Sou?ichiro)|"+                // -> Sōichirō
            "(Vivian)|"+                    // -> Viviane
            "(Tōsaka)|"+                    // -> Tohsaka
            "([ÉéEe]v[ée]nement)|"+         // -> évènement
            "([Pp]éron\\b)|"+               // -> perron
            "([Dd]inner\\b)"+               // -> Dîner
            ")"
        }, {"inconsistance avec les règles établies",
            "(\\bQ-Qu)|"+                   // -> Qu-Qu
            "(\\b[Gg]eez\\b)|"+             // -> tss / bon sang
            "(\\b[Hh]ey\\b)|"+              // -> Hé
            "(\\b[Ww]ow\\b)|"+              // -> Waouh / Ouah / Oh
            "(\\b[Ss]igh*\\b)|"+            // -> Pff*
            "(\\b(([Uu]ne)|([LlSs]a))\\s((Master)|(Servant)))" // masculin
        }, {"minuscule au nom propre",
            "\\b("+
            "(masters?)|"+
            "(mages?)"+
            ")\\b"
        }, {"plusieurs espaces",
            "\\S\\s\\1+\\S"
        }, {"erreur de script",
            "r\\]."                         // -> [(l)r] doit être en fin de ligne
        }
    });

    private static String fixNbsp(String line) {
        return missingNbspRegex.matcher(line).replaceAll("\u00A0");
    }

    private static String fixSuspensionPoints(String line, BiFunction<String, Integer, Void> report) {
        // remplace "..." par "…"
        int index = 0;
        while (index < line.length() && (index = line.indexOf("..", index)) != -1) {
            if (line.startsWith("...", index) && !line.startsWith("....", index)) {
                report.apply("mauvais points de suspension (corrigé auto.)", index);
                line = line.substring(0, index) + "…" + line.substring(index+3);

            } else {
                report.apply("plusieurs points d'affilée", index);
                do {
                    index += 2;
                } while(index < line.length() && line.charAt(index) == '.');
            }
        }
        return line;
    }

    private static String fixApostrophes(String line, BiFunction<String, Integer, Void> report) {
        Matcher straightApostropheMatcher = apostropheRegex.matcher(line);
        while (straightApostropheMatcher.find()) {
            report.apply("apostrophe droite (corrigé auto.)", straightApostropheMatcher.start());
        }
        // remplace les apostrophes droites par des apostrophes courbes sauf si précédées ou suivies d'un espace
        line = straightApostropheMatcher.replaceAll("’");
        return line;
    }

    private static String fixAlinea(String line, int neededAlinea, BiFunction<String, Integer, Void> report) {
        int alinea = 0;

        while(Character.isWhitespace(line.charAt(alinea)))
            alinea++;
        
        if (alinea != neededAlinea) {
            switch(alinea) {
                case 0 :
                    report.apply(String.format("alinea de %d caractères attendu", neededAlinea), -1);
                    break;
                case 2 : case 3 :
                    report.apply(String.format("alinea de %d caractères au lieu de %d (corrigé auto.)",
                                 alinea, neededAlinea), -1);
                    line = " ".repeat(neededAlinea) + line.substring(alinea);
                case 1 : case 4 :
                    if (neededAlinea == 0) {
                        if( alinea > 1)
                            report.apply(String.format("alinea de %d caractères inattendu", alinea), -1);
                    }
                    else {
                        report.apply(String.format("alinea de %d caractère au lie de %d",
                                     alinea, neededAlinea), -1);
                    }
                default : break; // ignore alineas > 4 caractères
            }
        }
        return line;
    }
    private static String fixQuotes(String line, boolean inQuote, BiFunction<String, Integer, Void> report) {
        int straightQuoteIndex = line.indexOf('"');
        while(straightQuoteIndex >= 0) {
            String before = line.substring(0, straightQuoteIndex);
            int leftBracketsBefore = (int) leftBracketRegex.matcher(before).results().count();
            boolean inBrackets;
            if (leftBracketsBefore > 0) {
                int rightBracketsBefore = (int) rightBracketRegex.matcher(before).results().count();
                inBrackets = (leftBracketsBefore > rightBracketsBefore);
            } else {
                inBrackets = false;
            }

            int alinea = 0;
            while(Character.isWhitespace(line.charAt(alinea)))
                alinea++;
            
            if (inBrackets) { //TODO check if preceded by \w+= and followed by \s*((\w+=)|\])
                // quote inside brackets. Leave as is.
            } else if (straightQuoteIndex == alinea) {
                report.apply("mauvais guillemets (corrigé auto.)", straightQuoteIndex);
                line = line.substring(0, straightQuoteIndex) + "“" + line.substring(straightQuoteIndex+1);
            } else if (line.substring(straightQuoteIndex).isBlank()) {
                report.apply("mauvais guillemets (corrigé auto.)", straightQuoteIndex);
                line = line.substring(0, straightQuoteIndex) + "”" + line.substring(straightQuoteIndex+1);
            } else {
                report.apply("mauvais guillemets", straightQuoteIndex);
            }
            straightQuoteIndex = line.indexOf('"', straightQuoteIndex+1);
        }
        straightQuoteIndex = line.indexOf('\'');
        while(straightQuoteIndex >= 0) {
            boolean quoteInQuote = inQuote ?
                    line.lastIndexOf('«', straightQuoteIndex) >= line.lastIndexOf('»', straightQuoteIndex)
                    : line.lastIndexOf('«', straightQuoteIndex) > line.lastIndexOf('»', straightQuoteIndex);
            if (!quoteInQuote) {
                report.apply("mauvais guillemets", straightQuoteIndex);
            } else {
                String apostrophe = null;
                if (Character.isWhitespace(line.charAt(straightQuoteIndex-1))) {
                    apostrophe = "‘";
                } else if (Character.isWhitespace(line.charAt(straightQuoteIndex+1))) {
                    apostrophe = "’";
                }
                else {
                    report.apply("mauvaise apostrophe", straightQuoteIndex);
                }
                if(apostrophe != null) {
                    report.apply("mauvaise apostrophe (corrigé auto.)", straightQuoteIndex);
                    line = line.substring(0, straightQuoteIndex) + apostrophe + line.substring(straightQuoteIndex+1);
                }
            }
            straightQuoteIndex = line.indexOf('\'', straightQuoteIndex+1);
        }
        return line;
    }
    private static void reportErrors(String line, BiFunction<String, Integer, Void> report) {
        for(ErrorDetectPattern edp : errorPatterns) {
            Matcher matcher = edp.matcher(line);
            while (matcher.find()) {
                report.apply(edp.msg, matcher.start());
            }
        }
    }

    /**
     * Replaces all docx tags with the appropriate text equivalent.
     * Removes the first \r at the top of the file
     * @param content - the input string to convert
     * @return the converted string
     */
    public static String docxToTxt(String content) {
		return content.replaceAll("</w:p>", "\n")
                      .replaceAll("<[^>]*/?>", "")
                      .replaceAll("&amp;", "&")
                      .replaceAll("&quot;", "\"")
                      .replaceAll("&lt;", "<")
                      .replaceAll("&gt;", ">")
                      .replaceFirst("\r", "");
    }

    /**
     * Fixes syntax issues in the document.
     * rules taken into account : <ul>
     *  <li> 2 spaces before paragraph, 3 for continued dialog (same speaker); </li>
     *  <li> no straight ' or " except in formulas </li>
     *  <li> « » for quotations, and curved '' for quotations inside quotations </li>
     * </ul>
     * @param content - the input string to fix
     * @return the fixed string
     * @throws Exception
     */
    public static String fixScenarioFile(String fileName, String text) throws Exception {
        StringBuilder builder = new StringBuilder();

        boolean talking = false;
        boolean talkingBeforeIf = false;
        boolean talkingEndofIf = false;
        boolean inQuote = false;
        boolean inQuoteBeforeIf = false;
        boolean inQuoteEndofIf = false;
        boolean needAlinea = true;
        boolean alineaBeforeIf = true;
        boolean alineaEndofIf = true;
        int branchState = 0; // 1 : if, 2: else
        String waitTextReport = null;

        final Iterator<String> lineIterator = text.lines().iterator();
        final FinalContainer<String> _line = new FinalContainer<>("");
        final FinalContainer<Integer> lineNumber = new FinalContainer<>(0);
        final FinalContainer<Integer> pageNumber = new FinalContainer<>(0);

        final BiFunction<String, Integer, Void> report = (msg, column) -> {
            String extract;
            if (_line.get().length() <= 70)
                extract = _line.get();
            else {
                int idxStart = Math.max(0, column-35);
                int idxEnd = Math.min(_line.get().length(), idxStart + 70);
                idxStart = idxEnd - 70;
                extract = _line.get().substring(idxStart, idxEnd);
                column = column-idxStart;
            }
            String message = String.format("%20s : #%4d @ page %3d : %s\n%s\n",
                                            fileName, lineNumber.get()-1,
                                            pageNumber.get(), msg, extract);
            if (column >= 0)
                message += " ".repeat(column)+"*";

            Utils.print(message, Utils.SYNTAX);
            return null;
        };

        while(lineIterator.hasNext()) {
            String line = lineIterator.next();
            _line.set(line);
            lineNumber.set(lineNumber.get()+1);
            if (line.startsWith(";")) {
                //comment, ignored
            }
            else if (line.startsWith("*page")) {
                int pipeIndex = line.indexOf('|');
                if (pipeIndex == -1) {
                    report.apply("marquage de page incomplet (corrigé auto.)", -1);
                    pipeIndex = "*page".length();
                    while (pipeIndex < line.length() && Character.isDigit(line.charAt(pipeIndex)))
                        pipeIndex++;
                    String after = pipeIndex < line.length() ? line.substring(pipeIndex) : "";
                    line = line.substring(0, pipeIndex) + "|" + after;
                }
                pageNumber.set(Integer.parseInt(line, "*page".length(), pipeIndex, 10)+1);
            }
            else if (line.startsWith("@")) {
                if (line.startsWith("@r")) {
                    needAlinea = true;
                } else if (line.startsWith("@pg")) {
                    needAlinea = true;
                    waitTextReport = null;
                    if (talking)
                        report.apply("dialogue non terminé à la fin de la page", -1);
                    if (inQuote)
                        report.apply("citation non terminée à la fin de la page", -1);
                    needAlinea = true;
                } else if (line.startsWith("@if")) {
                    talkingBeforeIf = talking;
                    inQuoteBeforeIf = inQuote;
                    alineaBeforeIf = needAlinea;
                    branchState = 1;
                } else if (line.startsWith("@else")) {
                    talkingEndofIf = talking;
                    inQuoteEndofIf = inQuote;
                    alineaEndofIf = needAlinea;
                    talking = talkingBeforeIf;
                    inQuote = inQuoteBeforeIf;
                    needAlinea = alineaBeforeIf;
                    branchState = 2;
                } else if (line.startsWith("@endif")) {
                    if (branchState == 2) { // 'if', 'else'
                        if (talking != talkingEndofIf)
                            report.apply("problème de dialogue au niveau du if/else", -1);
                        if (inQuote != inQuoteEndofIf)
                            report.apply("problème de citation au niveau du if/else", -1);
                        if (needAlinea != alineaEndofIf)
                            waitTextReport = "problème de paragraphe au niveau du if/else précédent";
                    } else if (branchState == 1) { // 'if' only
                        if (talking != talkingBeforeIf)
                            report.apply("problème de dialogue au niveau du if", -1);
                        if (inQuote != inQuoteBeforeIf)
                            report.apply("problème de citation au niveau du if", -1);
                        if (needAlinea != alineaBeforeIf)
                            waitTextReport = "problème de paragraphe au niveau du if précédent";
                    }
                }
                // TODO peut-être rajouter des commandes à interpréter manuellement ?
            } else if (!line.isBlank()) {
                if (waitTextReport != null) {
                    report.apply(waitTextReport, -1);
                    waitTextReport = null;
                }
                // remplace les espaces par des espaces insécables au niveau des ponctuations et des « »
                line = fixNbsp(line);
                // remplace les apostrophes droites par des apostrophes courbes sauf si précédées ou suivies d'un espace
                line = fixApostrophes(line, report);
                // remplace "..." par "…"
                line = fixSuspensionPoints(line, report);

                _line.set(line);

                //corrige les alineas 2/3 -> 3/2, report tous les alineas anormaux
                line = fixAlinea(line, needAlinea ? (talking || inQuote) ? 3 : 2 : 0, report);

                _line.set(line);

                line = fixQuotes(line, inQuote, report);

                int startDialogIndex = line.indexOf('“');
                int endDialogIndex = line.lastIndexOf('”');
                if (startDialogIndex >= 0) {
                    if (!line.substring(0, startDialogIndex).isBlank()) {
                        report.apply("mauvais guillemets", startDialogIndex);
                    }
                    if (talking) {
                        report.apply("dialogue précédent non terminé", startDialogIndex);
                        if (endDialogIndex >= 0)
                            talking = false;
                    }
                    else {
                        if (endDialogIndex < startDialogIndex)
                            talking = true;
                        if (inQuote) {
                            report.apply("dialogue dans une citation", startDialogIndex);
                            inQuote = false;
                        }
                    }
                } else if (endDialogIndex >= 0) {
                    if (!talking)
                        report.apply("\naucun dialogue à terminer", endDialogIndex);
                    else {
                        talking = false;
                    }
                }

                int leftQuotesCount = (int) leftFrenchQuoteRegex.matcher(line).results().count();
                int rightQuotesCount = (int) rightFrenchQuoteRegex.matcher(line).results().count();
                if (leftQuotesCount != rightQuotesCount) {
                    if (Math.abs(leftQuotesCount - rightQuotesCount) > 1) {
                        report.apply("guillemets « » non équilibrés", -1);
                    } else if (leftQuotesCount == 0 && !inQuote)
                        report.apply("aucune citation (« ») à terminer", -1);
                    else if (rightQuotesCount == 0 && inQuote)
                        report.apply("citation (« ») déjà en cours", -1);
                    else {
                        //other errors are easily spotted by reading.
                        inQuote = !inQuote;
                    }
                }
                else {
                    int leftQuoteIndex = -1;
                    int rightQuoteIndex = -1;
                    do {
                        leftQuoteIndex = line.indexOf('«', leftQuoteIndex+1);
                        rightQuoteIndex = line.indexOf('»', rightQuoteIndex+1);

                    } while(leftQuoteIndex >= 0 && rightQuoteIndex >= 0 && (inQuote == (rightQuoteIndex < leftQuoteIndex)));
                    if (leftQuoteIndex >= 0 || rightQuoteIndex >= 0) {
                        report.apply(String.format("guillemets « » dans le mauvais ordre. Début de la ligne %s citation", inQuote ? "dans une" : "hors"), -1);
                    }
                }

                reportErrors(line, report);

                if (line.endsWith("r]"))
                    needAlinea = true;
                else
                    needAlinea = false;
            }
            builder.append(line).append("\n");
        }
        if (talking) {
            report.apply("dialogue non terminé à la fin du fichier", -1);
        }
        if (inQuote) {
            report.apply("citation non terminée à la fin du fichier", -1);
        }
		return builder.toString();
    }

    public static String fixTranslationFile(String fileName, String text) {

        StringBuilder builder = new StringBuilder();
        String location = null;
        String msgctxt = null;
        String msgid = null;
        String msgstr = null;
        int currentStep = 0; // 1 : location, 2: msgctxt, 3: msgid, 4: msgstr
        
        final Iterator<String> lineIterator = text.lines().iterator();
        final FinalContainer<String> _line = new FinalContainer<>("");
        final FinalContainer<Integer> lineNumber = new FinalContainer<>(0);

        final BiFunction<String, Integer, Void> report = (msg, column) -> {
            String extract;
            if (_line.get().length() <= 70)
                extract = _line.get();
            else {
                int idxStart = Math.max(0, column-35);
                int idxEnd = Math.min(_line.get().length(), idxStart + 70);
                idxStart = idxEnd - 70;
                extract = _line.get().substring(idxStart, idxEnd);
                column = column-idxStart;
            }
            String message = String.format("%s:%d: %s\n%s\n",
                                            fileName, lineNumber.get(),
                                            msg, extract);
            if (column >= 0)
                message += " ".repeat(column)+"*";

            Utils.print(message, Utils.SYNTAX);
            return null;
        };

        while (lineIterator.hasNext()) {
            String line = lineIterator.next();
            _line.set(line);
            lineNumber.set(lineNumber.get()+1);
            int quoteIndex = -1;
            String lineText = null;
            if (line.startsWith("#:")) {
                //previous entry not finished => report error
                switch(currentStep) {
                    case 2 : case 3 :
                        report.apply("entrée non terminée", -1);
                        // report error : unfinished entry
                    case 4 : // check previous entry ?
                        Iterator<String> strLineIterator = msgstr.lines().iterator();
                        while (strLineIterator.hasNext()) {
                            String strLine = strLineIterator.next();
                            reportErrors(strLine, report);
                        }
                        // no break on purpose
                    case 0 :
                        location = line;
                        msgctxt = null;
                        msgid = null;
                        msgstr = null;
                        currentStep = 1;
                        break;
                    case 1 :
                        location += line.substring(2); // skip "#:"
                        break;
                }
            } else if ((quoteIndex = line.indexOf('"')) >= 0) {
                lineText = line.substring(quoteIndex+1, line.lastIndexOf('"')).replace("\\n", "\n");
            }
            if (line.startsWith("msg")) {
                switch(currentStep) {
                    case 0 : break;
                    case 1 :
                        if (line.startsWith("ctxt", 3)) {
                            currentStep = 2;
                            msgctxt = lineText;
                        } else if (line.startsWith("id", 3)) {
                            currentStep = 3;
                            msgid = lineText;
                        }
                        else
                            report.apply("la ligne devrait commencer par '#:', 'msgctxt' ou 'msgid'", -1);
                        break;
                    case 2 :
                        if (line.startsWith("id", 3)) {
                            currentStep = 3;
                            msgid = lineText;
                        }
                        else
                            report.apply("la ligne devrait être un texte ou commencer par 'msgid'", -1);
                        break;
                    case 3 :
                        if (line.startsWith("str", 3)) {
                            currentStep = 4;
                            lineText = fixNbsp(lineText);
                            lineText = fixApostrophes(lineText, report);
                            lineText = fixSuspensionPoints(lineText, report);
                            msgstr = lineText;
                            line = line.substring(0,quoteIndex)
                                 + '"' + lineText + '"'
                                 + line.substring(line.lastIndexOf('"')+1);
                        }
                        else if (line.startsWith("id_plural", 3)) {
                            // plurals not yet handled
                        }
                        else
                            report.apply("la ligne devrait être un texte ou commencer par 'msgstr'", -1);
                        break;
                    case 4 :
                        if (line.startsWith("str_plural", 3)) {
                            // plurals not yet handled
                        }
                        report.apply("entrée actuelle non terminée", -1);
                        break;
                }
            }
            else if (quoteIndex >= 0) {
                switch(currentStep) {
                    case 0: break;
                    case 1: report.apply("guillemet inattendu pour une position dans un fichier", quoteIndex);
                    case 2: msgctxt += lineText; break;
                    case 3: msgid   += lineText; break;
                    case 4:
                        lineText = fixNbsp(lineText);
                        lineText = fixApostrophes(lineText, report);
                        lineText = fixSuspensionPoints(lineText, report);
                        msgstr  += lineText;
                        line = line.substring(0,quoteIndex)
                             + '"' + lineText + '"'
                             + line.substring(line.lastIndexOf('"')+1);
                        break;
                }
            }
            builder.append(line+"\n");
        }
        return builder.toString().trim();
    }
}
