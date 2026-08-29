/**
 * "3 days ago" rather than "8/29/2026".
 *
 * A marketplace is judged on whether anything is happening; an absolute date
 * makes the reader do that arithmetic themselves, and makes a quiet week look
 * like an abandoned site.
 */
export function relativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";

  const seconds = Math.floor((Date.now() - then) / 1000);
  if (seconds < 60) return "just now";

  const units: Array<[number, string]> = [
    [60, "minute"],
    [24, "hour"],
    [7, "day"],
    [4.35, "week"],
    [12, "month"],
  ];

  let value = seconds / 60;
  let label = "minute";
  for (let i = 0; i < units.length; i++) {
    if (value < units[i][0] || i === units.length - 1) {
      label = units[i][1];
      break;
    }
    value = value / units[i][0];
    label = units[i + 1] ? units[i][1] : label;
  }

  const rounded = Math.floor(value);
  return `${rounded} ${label}${rounded === 1 ? "" : "s"} ago`;
}
