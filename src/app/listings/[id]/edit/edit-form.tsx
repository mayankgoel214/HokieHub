"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { ArrowLeft, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  ApiError,
  deleteListingImage,
  listCategories,
  updateListing,
  uploadListingImage,
} from "@/lib/api";
import { createClient } from "@/lib/supabase/client";
import type { Category, DefectInput, Listing } from "@/types/api";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function EditListingForm({ listing }: { listing: Listing }) {
  const router = useRouter();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [title, setTitle] = useState(listing.title);
  const [description, setDescription] = useState(listing.description);
  const [price, setPrice] = useState(String(listing.price));
  const [condition, setCondition] = useState(listing.condition ?? "");
  const [location, setLocation] = useState(listing.location ?? "");
  const [status, setStatus] = useState(listing.status);
  const [categoryId, setCategoryId] = useState(listing.category.id);

  const [defects, setDefects] = useState<DefectInput[]>(
    listing.defects.map((d) => ({
      description: d.description,
      severity: d.severity,
    })),
  );

  // Photographs already on the listing, and ones being added. Removals happen
  // immediately rather than on save, because they are their own endpoint and
  // batching them would mean holding a delete that might never be confirmed.
  const [existing, setExisting] = useState(listing.images);
  const [newPhotos, setNewPhotos] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);

  const [categories, setCategories] = useState<Category[]>([]);
  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  async function token(): Promise<string> {
    const supabase = createClient();
    const {
      data: { session },
    } = await supabase.auth.getSession();
    if (!session) throw new Error("Your session has expired. Sign in again.");
    return session.access_token;
  }

  async function removePhoto(imageId: number) {
    setError(null);
    try {
      await deleteListingImage(listing.id, imageId, await token());
      setExisting(existing.filter((i) => i.id !== imageId));
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "That photograph could not be removed.",
      );
    }
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);

    try {
      const t = await token();

      await updateListing(
        listing.id,
        {
          title,
          description,
          price: Number(price),
          condition: condition === "" ? undefined : (condition as never),
          location: location === "" ? undefined : location,
          status,
          categoryId,
          // Always sent, so clearing the list clears the disclosures. Omitting
          // it would mean "leave them alone", and a seller who deleted every row
          // would find them still there.
          defects: defects.filter((d) => d.description.trim() !== ""),
        },
        t,
      );

      const failures: string[] = [];
      for (const photo of newPhotos) {
        try {
          await uploadListingImage(listing.id, photo, t);
        } catch (e) {
          failures.push(e instanceof Error ? e.message : photo.name);
        }
      }

      if (failures.length > 0) {
        setError(`Saved, but a photograph did not upload: ${failures[0]}`);
        setSaving(false);
        return;
      }

      router.push(`/listings/${listing.id}`);
      router.refresh();
    } catch (e) {
      setError(
        e instanceof ApiError || e instanceof Error
          ? e.message
          : "That could not be saved.",
      );
      setSaving(false);
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto max-w-2xl px-4 py-8">
        <Link
          href={`/listings/${listing.id}`}
          className="text-muted-foreground hover:text-foreground mb-6 inline-flex items-center gap-1.5 text-sm"
        >
          <ArrowLeft className="size-4" />
          Back to the listing
        </Link>

        <Card>
          <CardHeader>
            <CardTitle>Edit listing</CardTitle>
            <CardDescription>
              Changes are live as soon as you save. Offers already made are
              kept.
            </CardDescription>
          </CardHeader>

          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-6">
              {error && (
                <div className="border-destructive/40 bg-destructive/5 text-destructive rounded-lg border px-4 py-3 text-sm">
                  {error}
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="title">Title *</Label>
                <Input
                  id="title"
                  value={title}
                  maxLength={255}
                  required
                  onChange={(e) => setTitle(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="description">Description *</Label>
                <Textarea
                  id="description"
                  value={description}
                  rows={4}
                  required
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="price">Price *</Label>
                  <Input
                    id="price"
                    type="number"
                    step="0.01"
                    min="0"
                    value={price}
                    required
                    onChange={(e) => setPrice(e.target.value)}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="status">Status</Label>
                  <Select
                    value={status}
                    onValueChange={(v) => setStatus(v as Listing["status"])}
                  >
                    <SelectTrigger id="status">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="available">Available</SelectItem>
                      <SelectItem value="pending">Pending</SelectItem>
                      <SelectItem value="sold">Sold</SelectItem>
                      <SelectItem value="unavailable">Unavailable</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="condition">Condition</Label>
                  <Select value={condition} onValueChange={setCondition}>
                    <SelectTrigger id="condition">
                      <SelectValue placeholder="Select condition" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="new">New</SelectItem>
                      <SelectItem value="like_new">Like new</SelectItem>
                      <SelectItem value="good">Good</SelectItem>
                      <SelectItem value="fair">Fair</SelectItem>
                      <SelectItem value="poor">Poor</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="location">Location</Label>
                  <Input
                    id="location"
                    value={location}
                    onChange={(e) => setLocation(e.target.value)}
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="category">Category *</Label>
                <Select
                  value={String(categoryId)}
                  onValueChange={(v) => setCategoryId(Number(v))}
                  disabled={categories.length === 0}
                >
                  <SelectTrigger id="category">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((parent) => [
                      <SelectItem key={parent.id} value={String(parent.id)}>
                        {parent.name}
                      </SelectItem>,
                      ...parent.children.map((child) => (
                        <SelectItem key={child.id} value={String(child.id)}>
                          &nbsp;&nbsp;{child.name}
                        </SelectItem>
                      )),
                    ])}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>Photographs</Label>
                {existing.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {existing.map((image) => (
                      <div key={image.id} className="relative">
                        <Image
                          src={`${API_URL}${image.imageUrl}`}
                          alt=""
                          width={80}
                          height={80}
                          unoptimized
                          className="size-20 rounded-md border object-cover"
                        />
                        <button
                          type="button"
                          aria-label="Remove this photograph"
                          onClick={() => removePhoto(image.id)}
                          className="bg-background absolute -top-2 -right-2 rounded-full border p-1 shadow-sm"
                        >
                          <Trash2 className="size-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
                <Input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  multiple
                  onChange={(e) => {
                    const chosen = Array.from(e.target.files ?? [])
                      .filter((f) => f.size <= 2 * 1024 * 1024)
                      .slice(0, 5 - existing.length);
                    previews.forEach(URL.revokeObjectURL);
                    setNewPhotos(chosen);
                    setPreviews(chosen.map((f) => URL.createObjectURL(f)));
                  }}
                />
                {previews.length > 0 && (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {previews.map((src) => (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        key={src}
                        src={src}
                        alt=""
                        className="size-20 rounded-md border object-cover"
                      />
                    ))}
                  </div>
                )}
              </div>

              <div className="space-y-2">
                <Label>What is wrong with it?</Label>
                {defects.map((defect, i) => (
                  <div key={i} className="flex gap-2">
                    <Input
                      value={defect.description}
                      maxLength={200}
                      placeholder="e.g. Scratch on the lid"
                      onChange={(e) =>
                        setDefects(
                          defects.map((d, j) =>
                            j === i ? { ...d, description: e.target.value } : d,
                          ),
                        )
                      }
                    />
                    <Select
                      value={defect.severity}
                      onValueChange={(v) =>
                        setDefects(
                          defects.map((d, j) =>
                            j === i
                              ? { ...d, severity: v as DefectInput["severity"] }
                              : d,
                          ),
                        )
                      }
                    >
                      <SelectTrigger className="w-36">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="minor">Minor</SelectItem>
                        <SelectItem value="moderate">Moderate</SelectItem>
                        <SelectItem value="major">Major</SelectItem>
                      </SelectContent>
                    </Select>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label="Remove this fault"
                      onClick={() =>
                        setDefects(defects.filter((_, j) => j !== i))
                      }
                    >
                      &times;
                    </Button>
                  </div>
                ))}
                {defects.length < 10 && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      setDefects([
                        ...defects,
                        { description: "", severity: "minor" },
                      ])
                    }
                  >
                    Add a fault
                  </Button>
                )}
              </div>

              <div className="flex gap-4">
                <Button type="submit" disabled={saving} className="flex-1">
                  {saving ? "Saving…" : "Save changes"}
                </Button>
                <Link href={`/listings/${listing.id}`} className="flex-1">
                  <Button type="button" variant="outline" className="w-full">
                    Cancel
                  </Button>
                </Link>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
