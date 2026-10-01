# Check an upstream rapidhash update

Run these commands from the repository root. The vector generator uses the
header in `native/rapidhash`.

1. Check out the desired revision in `native/rapidhash`.
2. Run `./gradlew test` against the existing vectors.
3. Generate vectors from the updated header:

   ```sh
   mkdir -p build/reference
   cc -std=c99 -O2 -DRAPIDHASH_UNROLLED -I native/rapidhash native/generate_rapidhash_v3_vectors.c -o build/reference/vectors
   build/reference/vectors > build/reference/rapidhash-v3.csv
   ```

4. Compare the generated vectors with the checked-in values:

   ```sh
   diff -u src/test/resources/rapidhash-v3.csv build/reference/rapidhash-v3.csv
   ```

If hashes change, review `rapidhash_internal` and update the streaming
implementation to match. Changed hashes also affect existing stored checksums.
Once you have reviewed and accepted the changes, replace the vectors and rerun
the tests:

```sh
cp build/reference/rapidhash-v3.csv src/test/resources/rapidhash-v3.csv
./gradlew test
```
