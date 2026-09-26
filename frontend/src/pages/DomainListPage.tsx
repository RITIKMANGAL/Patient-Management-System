import { useEffect, useState } from "react";
import { ApiError, apiRequest } from "../api/apiClient";
import { toPageResponse } from "../api/pageResponse";
import type { ReactNode } from "react";
import type { PageResponse } from "../types/api";

interface Column<T> {
  header: string;
  render: (item: T) => ReactNode;
}

interface DomainListPageProps<T> {
  title: string;
  eyebrow: string;
  apiPath: string;
  emptyMessage: string;
  getRowKey: (item: T) => string;
  columns: Column<T>[];
}

type LoadState<T> =
  | { status: "loading"; page: null; error: null }
  | { status: "loaded"; page: PageResponse<T>; error: null }
  | { status: "error"; page: null; error: string };

export function DomainListPage<T>({
  title,
  eyebrow,
  apiPath,
  emptyMessage,
  getRowKey,
  columns
}: DomainListPageProps<T>) {
  const [state, setState] = useState<LoadState<T>>({ status: "loading", page: null, error: null });

  useEffect(() => {
    let isCurrent = true;

    setState({ status: "loading", page: null, error: null });
    apiRequest<unknown>(apiPath)
      .then((response) => {
        if (isCurrent) {
          setState({ status: "loaded", page: toPageResponse<T>(response), error: null });
        }
      })
      .catch((error: unknown) => {
        if (isCurrent) {
          setState({ status: "error", page: null, error: toMessage(error) });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [apiPath]);

  return (
    <section className="resource-page" aria-labelledby={`${title.toLowerCase().replace(/\s+/g, "-")}-heading`}>
      <div className="page-heading">
        <p className="eyebrow">{eyebrow}</p>
        <h1 id={`${title.toLowerCase().replace(/\s+/g, "-")}-heading`}>{title}</h1>
      </div>

      {state.status === "loading" && (
        <div className="resource-state loading-state" aria-live="polite">
          <span className="loading-dot" aria-hidden="true" />
          Loading...
        </div>
      )}

      {state.status === "error" && (
        <div className="form-error" role="alert">
          {state.error}
        </div>
      )}

      {state.status === "loaded" && state.page.content.length === 0 && (
        <div className="resource-state empty-state">
          <strong>{emptyMessage}</strong>
          <span>Records will appear here when they are available.</span>
        </div>
      )}

      {state.status === "loaded" && state.page.content.length > 0 && (
        <div className="table-panel" aria-label={`${title} list`}>
          <table className="resource-table">
            <thead>
              <tr>
                {columns.map((column) => (
                  <th key={column.header} scope="col">
                    {column.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {state.page.content.map((item) => (
                <tr key={getRowKey(item)}>
                  {columns.map((column) => (
                    <td key={column.header}>{column.render(item)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function toMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Unable to load data";
}
