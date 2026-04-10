package com.waferalarm.web;

import com.waferalarm.domain.RuleType;
import com.waferalarm.domain.Severity;
import com.waferalarm.evaluator.BacktestRequest;
import com.waferalarm.evaluator.BacktestResult;
import com.waferalarm.evaluator.RuleBacktester;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/rules")
public class BacktestController {

    private final RuleBacktester backtester;

    public BacktestController(RuleBacktester backtester) {
        this.backtester = backtester;
    }

    @PostMapping("/backtest")
    public BacktestResult backtest(@RequestBody BacktestHttpRequest req) {
        return backtester.run(new BacktestRequest(
                req.parameterId(),
                req.ruleType(),
                req.severity(),
                req.absoluteDelta(),
                req.percentageDelta(),
                req.minimumBaseline(),
                req.from(),
                req.to()
        ));
    }

    public record BacktestHttpRequest(
            long parameterId,
            RuleType ruleType,
            Severity severity,
            Double absoluteDelta,
            Double percentageDelta,
            Double minimumBaseline,
            Instant from,
            Instant to
    ) {}
}
