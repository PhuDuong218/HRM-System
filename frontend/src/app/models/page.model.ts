/** Transport shapes remain distinct: adapting them must not change backend JSON. */
export interface SpringPage<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number?: number;
  size?: number;
}

export interface ItemPage<T> {
  items: T[];
  totalPages: number;
  totalElements: number;
  page: number;
  size: number;
}

export interface ListPage<T> { rows: T[]; page: number; pages: number; total: number; }

export function toListPage<T>(response: SpringPage<T> | ItemPage<T>): ListPage<T> {
  return 'content' in response
    ? {rows:response.content,page:response.number??0,pages:response.totalPages,total:response.totalElements}
    : {rows:response.items,page:response.page,pages:response.totalPages,total:response.totalElements};
}
