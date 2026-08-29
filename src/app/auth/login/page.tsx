"use client";

import { useState } from "react";
import { login, signInAsDemo } from "../actions";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function LoginPage() {
  // A failed demo sign-in comes back as a query parameter, since a form action
  // cannot return one.
  const fromQuery =
    typeof window !== "undefined"
      ? new URLSearchParams(window.location.search).get("error")
      : null;

  const [error, setError] = useState<string | null>(fromQuery);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);

    const formData = new FormData(event.currentTarget);
    const result = await login(formData);

    if (result?.error) {
      setError(result.error);
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-3xl font-bold">HokieHub</CardTitle>
          <CardDescription>
            Sign in with your Virginia Tech email
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Email address</Label>
                <Input
                  id="email"
                  name="email"
                  type="email"
                  required
                  placeholder="you@vt.edu"
                  className="focus:border-orange-500 focus:ring-orange-500"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  name="password"
                  type="password"
                  required
                  placeholder="Enter your password"
                  className="focus:border-orange-500 focus:ring-orange-500"
                />
              </div>
            </div>

            {error && (
              <div className="rounded-md bg-red-50 p-4">
                <p className="text-sm text-red-800">{error}</p>
              </div>
            )}

            <Button type="submit" disabled={loading} className="w-full">
              {loading ? "Signing in..." : "Sign in"}
            </Button>
          </form>

          {/* Posting needs a confirmed @vt.edu address, which is the right rule
              and also shuts out anyone evaluating this from outside Virginia
              Tech. This is a real account that already satisfied it, lent out. */}
          <div className="mt-6 border-t pt-6">
            <p className="text-muted-foreground mb-3 text-center text-sm text-pretty">
              No Virginia Tech address? Try it as a demo account — you can post,
              bid and use the price check.
            </p>
            <form action={signInAsDemo}>
              <Button type="submit" variant="outline" className="w-full">
                Continue as a demo account
              </Button>
            </form>
          </div>
        </CardContent>

        <CardFooter className="justify-center">
          <p className="text-center text-sm text-muted-foreground">
            Don&apos;t have an account?{" "}
            <Link
              href="/auth/signup"
              className="font-medium underline underline-offset-4 hover:text-primary"
            >
              Sign up
            </Link>
          </p>
        </CardFooter>
      </Card>
    </div>
  );
}
