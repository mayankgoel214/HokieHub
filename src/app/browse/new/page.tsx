"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
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
  createListing,
  listCategories,
  uploadListingImage,
  ApiError,
} from "@/lib/api";
import { createClient } from "@/lib/supabase/client";
import type {
  Category,
  DefectInput,
  CreateListingBody,
  ItemCondition,
  ListingType,
} from "@/types/api";

export default function CreateListingPage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [formData, setFormData] = useState<Partial<CreateListingBody>>({
    listingType: "item",
  });

  // The options used to be six hardcoded names against a database that has
  // thirty-two, and the ids did not line up with any of them: choosing
  // "Electronics" filed the listing under Textbooks. They come from the API now,
  // which is where the category tree actually lives.
  // Faults the seller declares. Kept as its own bit of state rather than folded
  // into formData because the shape is a list the user edits row by row.
  const [defects, setDefects] = useState<DefectInput[]>([]);

  // Photographs, held until the listing exists — an image needs a listing id to
  // attach to, so they are uploaded immediately after it is created.
  const [photos, setPhotos] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [uploadNote, setUploadNote] = useState<string | null>(null);

  const [categories, setCategories] = useState<Category[]>([]);
  const [categoriesFailed, setCategoriesFailed] = useState(false);

  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch(() => setCategoriesFailed(true));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError(null);

    try {
      // The API is a separate service, so the Supabase session cookie does not
      // reach it — the access token has to be sent explicitly.
      const supabase = createClient();
      const {
        data: { session },
      } = await supabase.auth.getSession();

      if (!session) {
        throw new Error("Your session has expired. Please sign in again.");
      }

      const created = await createListing(
        {
          ...(formData as CreateListingBody),
          defects: defects.filter((d) => d.description.trim() !== ""),
        },
        session.access_token,
      );

      // The listing is already saved by this point. A photograph that fails to
      // upload must not read as a failed listing, so these are reported and the
      // seller still lands on their listing rather than back on a form that has
      // already been submitted.
      const failures: string[] = [];
      for (const photo of photos) {
        try {
          await uploadListingImage(created.id, photo, session.access_token);
        } catch (uploadError) {
          failures.push(
            uploadError instanceof Error ? uploadError.message : photo.name,
          );
        }
      }

      if (failures.length > 0) {
        setError(
          `The listing was posted, but ${failures.length} photograph(s) did not upload: ${failures[0]}`,
        );
        setIsSubmitting(false);
        return;
      }

      router.push(`/listings/${created.id}`);
      router.refresh();
    } catch (err) {
      // ApiError already carries the API's own message, including the first
      // field-level validation failure.
      setError(
        err instanceof ApiError || err instanceof Error
          ? err.message
          : "An error occurred",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <div className="container mx-auto max-w-2xl px-4 py-8">
        <div className="mb-6">
          <Link href="/browse">
            <Button variant="ghost" size="sm">
              <ArrowLeft className="size-4" />
              Back to listings
            </Button>
          </Link>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Create New Listing</CardTitle>
            <CardDescription>
              Fill out the form below to create a new listing
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-6">
              {error && (
                <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                  {error}
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="listingType">Listing Type</Label>
                <Select
                  value={formData.listingType}
                  onValueChange={(value: ListingType) =>
                    setFormData({ ...formData, listingType: value })
                  }
                >
                  <SelectTrigger id="listingType">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="item">Item</SelectItem>
                    <SelectItem value="service">Service</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="title">Title *</Label>
                <Input
                  id="title"
                  required
                  placeholder="e.g., iPhone 13 Pro Max"
                  value={formData.title || ""}
                  onChange={(e) =>
                    setFormData({ ...formData, title: e.target.value })
                  }
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="description">Description *</Label>
                <Textarea
                  id="description"
                  required
                  placeholder="Provide a detailed description..."
                  rows={4}
                  value={formData.description || ""}
                  onChange={(e) =>
                    setFormData({ ...formData, description: e.target.value })
                  }
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="price">
                  Price * {formData.listingType === "service" && "(per hour)"}
                </Label>
                <Input
                  id="price"
                  type="number"
                  required
                  min="0"
                  step="0.01"
                  placeholder="0.00"
                  value={formData.price || ""}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      price: parseFloat(e.target.value),
                    })
                  }
                />
              </div>

              {formData.listingType === "item" && (
                <div className="space-y-2">
                  <Label htmlFor="condition">Condition</Label>
                  <Select
                    value={formData.condition}
                    onValueChange={(value: ItemCondition) =>
                      setFormData({ ...formData, condition: value })
                    }
                  >
                    <SelectTrigger id="condition">
                      <SelectValue placeholder="Select condition" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="new">New</SelectItem>
                      <SelectItem value="like_new">Like New</SelectItem>
                      <SelectItem value="good">Good</SelectItem>
                      <SelectItem value="fair">Fair</SelectItem>
                      <SelectItem value="poor">Poor</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="location">Location</Label>
                <Input
                  id="location"
                  placeholder="e.g., Blacksburg, VA"
                  value={formData.location || ""}
                  onChange={(e) =>
                    setFormData({ ...formData, location: e.target.value })
                  }
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="categoryId">Category *</Label>
                <Select
                  value={formData.categoryId?.toString() ?? ""}
                  onValueChange={(value) =>
                    setFormData({ ...formData, categoryId: parseInt(value) })
                  }
                  disabled={categories.length === 0}
                >
                  <SelectTrigger id="categoryId">
                    <SelectValue
                      placeholder={
                        categoriesFailed
                          ? "Categories could not be loaded"
                          : categories.length === 0
                            ? "Loading categories…"
                            : "Select a category"
                      }
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((parent) => [
                      <SelectItem key={parent.id} value={parent.id.toString()}>
                        {parent.name}
                      </SelectItem>,
                      ...parent.children.map((child) => (
                        <SelectItem key={child.id} value={child.id.toString()}>
                          &nbsp;&nbsp;{child.name}
                        </SelectItem>
                      )),
                    ])}
                  </SelectContent>
                </Select>
                {categoriesFailed && (
                  <p className="text-destructive text-sm">
                    The category list could not be loaded, so a listing cannot
                    be filed correctly. Reload the page to try again.
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="photos">Photographs</Label>
                <p className="text-muted-foreground text-sm">
                  Up to five, 2 MB each. A buyer can pay to have these read
                  against comparable listings, so a clear photograph is worth
                  more than a good description.
                </p>
                <Input
                  id="photos"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  multiple
                  onChange={(event) => {
                    const chosen = Array.from(event.target.files ?? []);
                    const tooBig = chosen.filter(
                      (f) => f.size > 2 * 1024 * 1024,
                    );
                    const usable = chosen
                      .filter((f) => f.size <= 2 * 1024 * 1024)
                      .slice(0, 5);

                    // Said here rather than after a failed round trip: the API
                    // enforces the same limits, but finding out at submit time
                    // is a worse way to learn them.
                    setUploadNote(
                      tooBig.length > 0
                        ? `${tooBig.length} file(s) were over 2 MB and were left out.`
                        : chosen.length > 5
                          ? "Only the first five were kept."
                          : null,
                    );

                    previews.forEach(URL.revokeObjectURL);
                    setPhotos(usable);
                    setPreviews(usable.map((f) => URL.createObjectURL(f)));
                  }}
                />
                {uploadNote && (
                  <p className="text-destructive text-sm">{uploadNote}</p>
                )}
                {previews.length > 0 && (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {previews.map((src, i) => (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        key={src}
                        src={src}
                        alt={`Photograph ${i + 1}`}
                        className="size-20 rounded-md border object-cover"
                      />
                    ))}
                    <p className="text-muted-foreground self-end text-xs">
                      The first is the card image.
                    </p>
                  </div>
                )}
              </div>

              <div className="space-y-2">
                <Label>What is wrong with it?</Label>
                <p className="text-muted-foreground text-sm">
                  Say it here and a buyer will not find out on arrival. Leave it
                  empty if there is genuinely nothing.
                </p>

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
                      onValueChange={(value) =>
                        setDefects(
                          defects.map((d, j) =>
                            j === i
                              ? {
                                  ...d,
                                  severity: value as DefectInput["severity"],
                                }
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
                <Button
                  type="submit"
                  disabled={isSubmitting || !formData.categoryId}
                  className="flex-1"
                >
                  {isSubmitting
                    ? photos.length > 0
                      ? "Posting and uploading…"
                      : "Posting…"
                    : "Create Listing"}
                </Button>
                <Link href="/browse" className="flex-1">
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
