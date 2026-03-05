import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

type RouteContext = {
  params:
    | {
        path?: string[];
      }
    | Promise<{
        path?: string[];
      }>;
};

function resolveBackendBaseUrl(): string | null {
  const raw =
    process.env.API_BASE_URL ??
    process.env.NEXT_PUBLIC_API_BASE_URL ??
    "";
  const normalized = raw.trim().replace(/\/$/, "");
  return normalized.length > 0 ? normalized : null;
}

async function forwardToBackend(
  request: NextRequest,
  context: RouteContext
): Promise<NextResponse> {
  const backendBaseUrl = resolveBackendBaseUrl();
  if (!backendBaseUrl) {
    return NextResponse.json(
      {
        code: "backend_url_missing",
        message:
          "Backend base URL is not configured. Set API_BASE_URL or NEXT_PUBLIC_API_BASE_URL.",
      },
      { status: 500 }
    );
  }

  const { path = [] } = await Promise.resolve(context.params);
  const targetPath = path.join("/");
  const targetUrl = `${backendBaseUrl}/api/${targetPath}${request.nextUrl.search}`;

  const headers = new Headers(request.headers);
  headers.delete("host");
  headers.delete("content-length");
  headers.delete("origin");
  headers.delete("referer");
  headers.set("x-forwarded-proto", request.nextUrl.protocol.replace(":", ""));
  headers.set("x-forwarded-host", request.headers.get("host") ?? "");

  const method = request.method.toUpperCase();
  const init: RequestInit = {
    method,
    headers,
    redirect: "manual",
    cache: "no-store",
  };

  if (method !== "GET" && method !== "HEAD") {
    const body = await request.arrayBuffer();
    if (body.byteLength > 0) {
      init.body = body;
    }
  }

  const upstream = await fetch(targetUrl, init);
  const responseHeaders = new Headers(upstream.headers);
  responseHeaders.delete("content-encoding");
  responseHeaders.delete("transfer-encoding");
  responseHeaders.delete("connection");

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: responseHeaders,
  });
}

export async function GET(request: NextRequest, context: RouteContext) {
  return forwardToBackend(request, context);
}

export async function POST(request: NextRequest, context: RouteContext) {
  return forwardToBackend(request, context);
}

export async function PUT(request: NextRequest, context: RouteContext) {
  return forwardToBackend(request, context);
}

export async function PATCH(request: NextRequest, context: RouteContext) {
  return forwardToBackend(request, context);
}

export async function DELETE(request: NextRequest, context: RouteContext) {
  return forwardToBackend(request, context);
}

export async function OPTIONS(request: NextRequest, context: RouteContext) {
  return forwardToBackend(request, context);
}
