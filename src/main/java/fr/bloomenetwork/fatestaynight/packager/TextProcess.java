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
    static private final Pattern apostropheRegex = Pattern.compile("(?<=[A-Za-zÀ-ÿ])(?=')(?=(?!’))(?=(?!‘)).(?=[A-Za-zÀ-ÿ]|$)");

    static private final Pattern missingNbspRegex = Pattern.compile(
        "((?<=«) )"+ // espace précédé de «
        "|( (?=»|:|;|\\?|!))"); // ou espace suivi de », :, ;, ? ou !

    static private final List<ErrorDetectPattern> errorPatterns = ErrorDetectPattern.fromArray(new String[][]{
        {"problème de ponctuation",
            "(!\\?)|"+                      // devrait être '?!'
            "(\\.\\s*…)|(…\\s*\\.)|"+       // doit être remplacé soit par '.', soit par '…'
            "(…\\w)|"+                      // Il doit y avoir un espace après '…' avant une lettre
            "(\\[line\\d+\\]\\.)|"+         // la ligne est équivalent à un point
            "(…[?!])"                       // manque espace insécable avant '?'/'!'
        }, {"mauvais usage",
            "(similaires?\\s*((à)|(au)))|"+ // A semblable à B / A et B sont similaires
            "([Dd]u coup)|"+                // uniquement si conséquence immédiate
            "([Aa]u final)|"+               // uniquement pour le final d'une représentation
            "([Pp]allier\\s+((à)|(au)))"    // uniquement pour le final d'une représentation
        }, {"phrase non terminée",
            "(?<!(\\.|“|!|\\?|…))"+         // dialogue non terminé si ne termine pas par ., (rien), !, ?, … ,
            "(?<!\\[line\\d\\])"+           // [lineX],
            "(?<!\\[line\\d{2}\\])"+        // ou [lineXX]
            "”"                             // avant le guillemet fermant
        }, {"mauvaise orthographe",
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
            "(Vivian\\b)|"+                 // -> Viviane
            "(Tōsaka)|"+                    // -> Tohsaka
            "([ÉéEe]v[ée]nement)|"+         // -> évènement
            "([Pp]éron)|"+                  // -> perron
            "(master)|"+                    // -> Master
            "(servant)|"+                   // -> Servant
            "([Dd]inner)"                   // -> Dîner
        }, {"Inconsistance avec les règles définies",
            "(\\bQ-Qu)|"+                   // -> Qu-Qu
            "(\\b[Gg]eez\\b)|"+             // -> tss / bon sang
            "(\\b[Hh]ey\\b)|"+              // -> Hé
            "(\\b[Ww]ow\\b)|"+              // -> Waouh / Ouah / Oh
            "(\\b[Ss]igh*\\b)|"+            // -> Pff*
            "(\\b(([Uu]ne)|([LlSs]a))\\s((Master)|(Servant)))" // masculin
        }, {"Minuscule au nom propre",
            "(master)|"+
            "(servant)|"+
            "(mage)"
        }, {"Plusieurs espaces",
            "\\S\\s\\1+\\S"
        }
    });

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
    public static String fixSyntax(String fileName, String text) throws Exception {
        StringBuilder builder = new StringBuilder();

        boolean talking = false;
        boolean talkingBeforeIf = false;
        boolean talkingEndofIf = false;
        boolean inQuote = false;
        boolean inQuoteBeforeIf = false;
        boolean inQuoteEndofIf = false;
        int branchState = 0; // 1 : if, 2: else
        boolean afterBranch = false;

        Iterator<String> lineIterator = text.lines().iterator();
        final FinalContainer<String> _line = new FinalContainer<>("");
        final FinalContainer<Integer> lineNumber = new FinalContainer<>(0);
        final FinalContainer<Integer> pageNumber = new FinalContainer<>(0);

        BiFunction<String, Integer, Void> report = (msg, column) -> {
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
                pageNumber.set(Integer.parseInt(line, "*page".length(), line.indexOf('|'), 10)+1);
                if (talking) {
                    report.apply("dialogue non terminé à la fin de la page", -1);
                }
                if (inQuote) {
                    report.apply("citation non terminée à la fin de la page", -1);
                }
            }
            else if (line.startsWith("@")) {
                if (line.startsWith("@if")) {
                    talkingBeforeIf = talking;
                    inQuoteBeforeIf = inQuote;
                    branchState = 1;
                    afterBranch = true;
                } else if (line.startsWith("@else")) {
                    talkingEndofIf = talking;
                    inQuoteEndofIf = inQuote;
                    talking = talkingBeforeIf;
                    inQuote = inQuoteBeforeIf;
                    branchState = 2;
                    afterBranch = true;
                } else if (line.startsWith("@endif")) {
                    if (branchState == 2) { // 'if', 'else'
                        if (talking != talkingEndofIf)
                            report.apply("problème de dialogue au niveau du if/else", -1);
                        if (inQuote != inQuoteEndofIf)
                            report.apply("problème de citation au niveau du if/else", -1);
                    } else if (branchState == 1) { // 'if' only
                        if (talking != talkingBeforeIf)
                            report.apply("problème de dialogue au niveau du if", -1);
                        if (inQuote != inQuoteEndofIf)
                            report.apply("problème de citation au niveau du if", -1);
                    }
                    afterBranch = true;
                }
                // TODO peut-être rajouter des commandes à interpréter manuellement ?
            } else if (!line.isBlank()){
                // remplace les espaces par des espaces insécables au niveau des ponctuations et des « »
                line = missingNbspRegex.matcher(line).replaceAll("\u00A0");
                // remplace les apostrophes droites par des apostrophes courbes sauf si précédées ou suivies d'un espace
                line = apostropheRegex.matcher(line).replaceAll("’");
                // remplace "..." par "…"
                line = line.replaceAll("\\.\\.\\.", "…");
                _line.set(line);
                
                int alinea = 0;
                
                while(Character.isWhitespace(line.charAt(alinea)))
                    alinea++;
                switch(alinea) {
                    case 0 : break;
                    case 2 :
                        if (talking) {
                            line = ' ' + line;
                            alinea = 3;
                        }
                        break;
                    case 3 :
                        if (!talking) {
                            line = line.substring(1);
                            alinea = 2;
                        }
                        break;
                    default :
                        if(!afterBranch)
                            report.apply(String.format("alinea de %d caractères inattendu", alinea), -1);
                        //TODO report ? could be manual indentation
                }
                _line.set(line);

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
                    if (inBrackets) { //TODO check if preceded by \w+= and followed by \s*((\w+=)|\])
                        // quote inside brackets. Leave as is.
                    } else if (straightQuoteIndex == alinea) {
                        line = line.substring(0, straightQuoteIndex) + "“" + line.substring(straightQuoteIndex+1);
                    } else if (line.substring(straightQuoteIndex).isBlank()) {
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
                        if(apostrophe != null)
                            line = line.substring(0, straightQuoteIndex) + apostrophe + line.substring(straightQuoteIndex+1); 
                    }
                    straightQuoteIndex = line.indexOf('\'', straightQuoteIndex+1);
                }

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

                for(ErrorDetectPattern edp : errorPatterns) {
                    Matcher matcher = edp.matcher(line);
                    while (matcher.find()) {
                        report.apply(edp.msg, matcher.start());
                    }
                }

                afterBranch = false;
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
}
