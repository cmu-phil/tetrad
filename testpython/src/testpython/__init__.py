import numpy as np
import fcit

print(np.version.version)

N = 1000
x = np.random.random((N, 1))
y = np.random.random((N, 1))
z = np.random.random((N, 2))

for i in range(1, 10):
    p = fcit.test(x, x)
    print(p)
