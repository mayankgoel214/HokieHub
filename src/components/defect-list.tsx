import { TriangleAlert } from "lucide-react";
import type { Defect, DefectSeverity } from "@/types/api";

/**
 * What the seller says is wrong with the item.
 *
 * Given its own block rather than left to the description, and shown with the
 * worst first, because the whole value of asking a seller to disclose is that
 * the buyer can find it without reading a paragraph.
 */
const SEVERITY_RANK: Record<DefectSeverity, number> = {
  major: 0,
  moderate: 1,
  minor: 2,
};

const SEVERITY_STYLE: Record<DefectSeverity, string> = {
  major: "bg-destructive/10 text-destructive border-destructive/30",
  moderate: "bg-amber-500/10 text-amber-700 border-amber-500/30",
  minor: "bg-muted text-muted-foreground border-border",
};

export function DefectList({ defects }: { defects: Defect[] }) {
  if (!defects || defects.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        The seller has not noted any faults.
      </p>
    );
  }

  const sorted = [...defects].sort(
    (a, b) => SEVERITY_RANK[a.severity] - SEVERITY_RANK[b.severity],
  );

  return (
    <ul className="space-y-2">
      {sorted.map((defect) => (
        <li
          key={defect.id}
          className={`flex items-start gap-2 rounded-md border px-3 py-2 text-sm ${SEVERITY_STYLE[defect.severity]}`}
        >
          <TriangleAlert className="mt-0.5 size-4 shrink-0" />
          <span className="wrap-anywhere">{defect.description}</span>
          <span className="ml-auto shrink-0 text-xs capitalize opacity-80">
            {defect.severity}
          </span>
        </li>
      ))}
    </ul>
  );
}
