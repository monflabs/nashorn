# Default parameters

A parameter can carry a default expression, evaluated at call time and only when the argument is
missing or `undefined` - so a default can call a function or refer to the parameters before it.
Each call evaluates the expression afresh; there is no shared default object to mutate by
accident.
