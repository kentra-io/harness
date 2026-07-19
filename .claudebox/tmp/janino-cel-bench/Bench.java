///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS dev.cel:cel:0.13.1
//DEPS org.codehaus.janino:janino:3.1.12
//JAVA 17

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.codehaus.janino.ExpressionEvaluator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Rough microbenchmark: cel-java interpreted eval vs Janino-compiled eval
 * for boolean data-quality rules over a simple record.
 * Not JMH-rigorous; single-threaded, warmup + best-of-N rounds.
 */
public class Bench {

    // The interface Janino-generated classes implement (direct call, no reflection).
    public interface Rule {
        boolean eval(long age, String email, String country, double amount, Pattern emailPattern);
    }

    static final int N_MSGS = 1024;
    static final int WARMUP = 300_000;
    static final int ITERS = 2_000_000;
    static final int ROUNDS = 3;

    static long[] ages = new long[N_MSGS];
    static String[] emails = new String[N_MSGS];
    static String[] countries = new String[N_MSGS];
    static double[] amounts = new double[N_MSGS];
    static Map<String, Object>[] prebuiltMaps;

    static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    public static void main(String[] args) throws Exception {
        genData();

        String[] celRules = {
            "age >= 18 && age < 65 && amount > 10.0",
            "country == 'US' || country == 'CA' || email.endsWith('.com')",
            "age >= 18 && email.matches('^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}$')"
        };
        String[] janinoRules = {
            "age >= 18 && age < 65 && amount > 10.0",
            "country.equals(\"US\") || country.equals(\"CA\") || email.endsWith(\".com\")",
            "age >= 18 && emailPattern.matcher(email).matches()"
        };
        String[] names = {"R1 numeric/logic", "R2 string equality/endsWith", "R3 regex match"};

        // --- compile once ---
        CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar("age", SimpleType.INT)
            .addVar("email", SimpleType.STRING)
            .addVar("country", SimpleType.STRING)
            .addVar("amount", SimpleType.DOUBLE)
            .setResultType(SimpleType.BOOL)
            .build();
        CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

        CelRuntime.Program[] programs = new CelRuntime.Program[celRules.length];
        Rule[] compiled = new Rule[janinoRules.length];
        for (int i = 0; i < celRules.length; i++) {
            CelAbstractSyntaxTree ast = celCompiler.compile(celRules[i]).getAst();
            programs[i] = celRuntime.createProgram(ast);
            ExpressionEvaluator ee = new ExpressionEvaluator();
            compiled[i] = ee.createFastEvaluator(janinoRules[i], Rule.class,
                new String[]{"age", "email", "country", "amount", "emailPattern"});
        }

        System.out.printf("JVM: %s %s, iters=%d, rounds=%d (best shown)%n%n",
            System.getProperty("java.vm.name"), System.getProperty("java.version"), ITERS, ROUNDS);
        System.out.printf("%-30s %14s %14s %14s %10s%n",
            "rule", "janino ns/op", "cel ns/op*", "cel+map ns/op", "ratio*");

        for (int i = 0; i < celRules.length; i++) {
            final CelRuntime.Program p = programs[i];
            final Rule r = compiled[i];

            double janino = bench(n -> {
                long t = 0;
                for (int k = 0; k < n; k++) {
                    int m = k & (N_MSGS - 1);
                    if (r.eval(ages[m], emails[m], countries[m], amounts[m], EMAIL_PATTERN)) t++;
                }
                return t;
            });

            // cel with prebuilt activation maps (pure eval cost)
            double cel = bench(n -> {
                long t = 0;
                try {
                    for (int k = 0; k < n; k++) {
                        int m = k & (N_MSGS - 1);
                        if ((Boolean) p.eval(prebuiltMaps[m])) t++;
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
                return t;
            });

            // cel including per-eval map construction (binding cost included)
            double celMap = bench(n -> {
                long t = 0;
                try {
                    for (int k = 0; k < n; k++) {
                        int m = k & (N_MSGS - 1);
                        Map<String, Object> vars = new HashMap<>(8);
                        vars.put("age", ages[m]);
                        vars.put("email", emails[m]);
                        vars.put("country", countries[m]);
                        vars.put("amount", amounts[m]);
                        if ((Boolean) p.eval(vars)) t++;
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
                return t;
            });

            System.out.printf("%-30s %14.1f %14.1f %14.1f %9.1fx%n",
                names[i], janino, cel, celMap, cel / janino);
        }
        System.out.println("\n* cel ns/op = eval over a prebuilt variable map; ratio = cel(prebuilt)/janino");

        // Mixed loop: all 3 rules per message -> polymorphic call site (closer to a real
        // multi-rule engine where the JIT cannot fully devirtualize/inline one rule).
        final Rule[] all = compiled;
        double mixedJanino = bench(n -> {
            long t = 0;
            for (int k = 0; k < n; k++) {
                int m = k & (N_MSGS - 1);
                for (Rule rr : all) {
                    if (rr.eval(ages[m], emails[m], countries[m], amounts[m], EMAIL_PATTERN)) t++;
                }
            }
            return t;
        });
        final CelRuntime.Program[] allP = programs;
        double mixedCel = bench(n -> {
            long t = 0;
            try {
                for (int k = 0; k < n; k++) {
                    int m = k & (N_MSGS - 1);
                    for (CelRuntime.Program pp : allP) {
                        if ((Boolean) pp.eval(prebuiltMaps[m])) t++;
                    }
                }
            } catch (Exception e) { throw new RuntimeException(e); }
            return t;
        });
        System.out.printf("%nmixed (3 rules/msg, polymorphic): janino %.1f ns/msg, cel %.1f ns/msg, ratio %.1fx%n",
            mixedJanino, mixedCel, mixedCel / mixedJanino);
    }

    interface Body { long run(int n); }

    static double bench(Body b) {
        long sink = b.run(WARMUP);
        double best = Double.MAX_VALUE;
        for (int r = 0; r < ROUNDS; r++) {
            long t0 = System.nanoTime();
            sink += b.run(ITERS);
            long t1 = System.nanoTime();
            best = Math.min(best, (t1 - t0) / (double) ITERS);
        }
        if (sink == 42) System.out.print("");  // keep sink alive
        return best;
    }

    @SuppressWarnings("unchecked")
    static void genData() {
        Random rnd = new Random(42);
        String[] cs = {"US", "CA", "DE", "PL", "GB"};
        prebuiltMaps = new Map[N_MSGS];
        for (int i = 0; i < N_MSGS; i++) {
            ages[i] = 10 + rnd.nextInt(70);
            emails[i] = rnd.nextInt(10) == 0
                ? "not-an-email"
                : "user" + rnd.nextInt(100000) + "@example" + rnd.nextInt(100) + ".com";
            countries[i] = cs[rnd.nextInt(cs.length)];
            amounts[i] = rnd.nextDouble() * 100;
            Map<String, Object> m = new HashMap<>(8);
            m.put("age", ages[i]);
            m.put("email", emails[i]);
            m.put("country", countries[i]);
            m.put("amount", amounts[i]);
            prebuiltMaps[i] = m;
        }
    }
}
