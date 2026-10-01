#include <inttypes.h>
#include <stdio.h>
#include "rapidhash.h"

/* Generate vectors from the header supplied with -I. */
int main(void) {
    static uint8_t data[1048576];
    const size_t sizes[] = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
        31, 32, 33, 47, 48, 49, 63, 64, 65, 79, 80, 81, 95, 96, 97,
        111, 112, 113, 223, 224, 225, 335, 336, 337,
        1023, 1024, 1025, 4096, 65536, 1048576
    };
    const uint64_t seeds[] = {
        0, 42, UINT64_MAX, UINT64_C(0x8000000000000000), UINT64_C(0x7fffffffffffffff)
    };
    uint32_t state = UINT32_C(0x12345678);
    for (size_t i = 0; i < sizeof(data); i++) {
        state ^= state << 13;
        state ^= state >> 17;
        state ^= state << 5;
        data[i] = (uint8_t)state;
    }

    puts("# rapidhash V3 reference vectors");
    puts("# Input: low byte after each xorshift32 step (13,17,5), initial state 0x12345678");
    puts("# length,seed_hex,hash_hex");
    for (size_t i = 0; i < sizeof(sizes) / sizeof(sizes[0]); i++) {
        for (size_t j = 0; j < sizeof(seeds) / sizeof(seeds[0]); j++) {
            printf("%zu,%016" PRIx64 ",%016" PRIx64 "\n", sizes[i], seeds[j],
                   rapidhash_withSeed(data, sizes[i], seeds[j]));
        }
    }
    return ferror(stdout) ? 1 : 0;
}
