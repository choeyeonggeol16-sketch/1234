# CalcStat dead MP initializer cleanup

Reference: Open_LU4_L2OFF, PCParam.h CPCParamDB and CreatureServer.cpp ValidateParameters.
The L1 pure parameter tables are retained; existing characters are still validated by L1Character. Character creation and stat redistribution use hypothetical inputs, not the live creature's derived values.

Removed four private MP growth tables, their builders and rule types. Since MinincreaseMp/MaxincreaseMp use mpGrowthBound and increaseMp calls them, the old tables had no consumer outside their initialization graph. HP_BASE remains. All public API bodies, live private methods, and active table contents were compared verbatim against 2cdff1c07e6e6f2e32686ef03f15746dd95c4097 before commit. No numerical changes, new fields, new objects or new execution paths. Deprecated public aliases remain pending branch-wide caller enumeration; removing them without proving all callers would be unsafe.

Added an executable Java regression against the baseline commit. The regression was not executed in this environment. Prior extracted-method test results were also not verified by an actual JVM run in those turns; do not treat those reports as pass evidence.
