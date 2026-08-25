// eslint-config-next 16 ships flat config directly, so the FlatCompat bridge
// this used to go through is gone — under ESLint 9 that bridge threw
// "Converting circular structure to JSON" and no file was ever linted.
import coreWebVitals from "eslint-config-next/core-web-vitals";
import typescript from "eslint-config-next/typescript";

const eslintConfig = [
  ...coreWebVitals,
  ...typescript,
  {
    ignores: [
      "node_modules/**",
      ".next/**",
      "out/**",
      "build/**",
      "next-env.d.ts",
    ],
  },
];

export default eslintConfig;
