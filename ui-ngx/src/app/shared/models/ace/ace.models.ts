///
/// Copyright © 2016-2022 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Ace } from 'ace-builds';
import { Observable } from 'rxjs/internal/Observable';
import { forkJoin, from, of } from 'rxjs';
import { map, mergeMap, publishReplay, refCount, tap } from 'rxjs/operators';

let aceModule: any;

const aceModes = ['text', 'java', 'html', 'css', 'json', 'javascript', 'markdown', 'protobuf'];
const aceThemes = ['textmate', 'github'];

export type AceMode = typeof aceModes[number];
export type AceTheme = typeof aceThemes[number];

let generalAceDependencies$: Observable<any>;
let textmateAceThemeDependency$: Observable<any>;
let githubAceThemesDependency$: Observable<any>;
let textModeAceDependencies$: Observable<any>;
let javaModeAceDependencies$: Observable<any>;
let htmlModeAceDependencies$: Observable<any>;
let cssModeAceDependencies$: Observable<any>;
let jsonModeAceDependencies$: Observable<any>;
let javascriptModeAceDependencies$: Observable<any>;
let markdownModeAceDependencies$: Observable<any>;
let protobufModeAceDependencies$: Observable<any>;

function loadAceDependencies(modes: AceMode[], themes: AceTheme[]): Observable<any> {
  const aceObservables: Observable<any>[] = [];
  aceObservables.push(loadGeneralAceDependencies());
  aceObservables.push(loadAceThemeDependencies(themes));
  modes.forEach(mode => {
    switch (mode) {
      case 'text':
        aceObservables.push(loadTextModeAceDependencies());
        break;
      case 'java':
        aceObservables.push(loadJavaModeAceDependencies());
        break;
      case 'html':
        aceObservables.push(loadHtmlModeAceDependencies());
        break;
      case 'css':
        aceObservables.push(loadCssModeAceDependencies());
        break;
      case 'json':
        aceObservables.push(loadJsonModeAceDependencies());
        break;
      case 'javascript':
        aceObservables.push(loadJavascriptModeDependencies());
        break;
      case 'markdown':
        aceObservables.push(loadMarkdownModeDependencies());
        break;
      case 'protobuf':
        aceObservables.push(loadProtobufModeDependencies());
        break;
    }
  });
  return forkJoin(aceObservables);
}

function loadGeneralAceDependencies(): Observable<any> {
  if (!generalAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/ext-language_tools')));
    aceObservables.push(from(import('ace-builds/src-noconflict/ext-searchbox')));
    generalAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return generalAceDependencies$;
}

function loadAceThemeDependencies(themes: AceTheme[]): Observable<any> {
  const aceObservables: Observable<any>[] = [];
  themes.forEach(mode => {
    switch (mode) {
      case 'textmate':
        aceObservables.push(loadTextmateAceThemeDependencies());
        break;
      case 'github':
        aceObservables.push(loadGithubAceThemeDependencies());
        break;
    }
  });
  return forkJoin(aceObservables);
}

function loadTextmateAceThemeDependencies(): Observable<any> {
  if (!textmateAceThemeDependency$) {
    textmateAceThemeDependency$ = from(import('ace-builds/src-noconflict/theme-textmate')).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return textmateAceThemeDependency$;
}

function loadGithubAceThemeDependencies(): Observable<any> {
  if (!githubAceThemesDependency$) {
    githubAceThemesDependency$ = from(import('ace-builds/src-noconflict/theme-github')).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return githubAceThemesDependency$;
}

function loadTextModeAceDependencies(): Observable<any> {
  if (!textModeAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-text')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/text')));
    textModeAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return textModeAceDependencies$;
}

function loadJavaModeAceDependencies(): Observable<any> {
  if (!javaModeAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-java')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/java')));
    javaModeAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return javaModeAceDependencies$;
}

function loadHtmlModeAceDependencies(): Observable<any> {
  if (!htmlModeAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-html')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/html')));
    htmlModeAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return htmlModeAceDependencies$;
}

function loadCssModeAceDependencies(): Observable<any> {
  if (!cssModeAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-css')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/css')));
    cssModeAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return cssModeAceDependencies$;
}

function loadJsonModeAceDependencies(): Observable<any> {
  if (!jsonModeAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-json')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/json')));
    jsonModeAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return jsonModeAceDependencies$;
}

function loadJavascriptModeDependencies(): Observable<any> {
  if (!javascriptModeAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-javascript')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/javascript')));
    javascriptModeAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return javascriptModeAceDependencies$;
}

function loadMarkdownModeDependencies(): Observable<any> {
  if (!markdownModeAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-markdown')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/markdown')));
    markdownModeAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return markdownModeAceDependencies$;
}

function loadProtobufModeDependencies(): Observable<any> {
  if (!protobufModeAceDependencies$) {
    const aceObservables: Observable<any>[] = [];
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-c_cpp')));
    aceObservables.push(from(import('ace-builds/src-noconflict/mode-protobuf')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/c_cpp')));
    aceObservables.push(from(import('ace-builds/src-noconflict/snippets/protobuf')));
    protobufModeAceDependencies$ = forkJoin(aceObservables).pipe(
      publishReplay(1),
      refCount()
    );
  }
  return protobufModeAceDependencies$;
}

export function getAce(mode?: AceMode|AceMode[], theme?: AceTheme|AceTheme[]): Observable<any> {
  let aceModule$: Observable<any> = of(null);
  const modes = Array.isArray(mode) ? mode : (mode ? [mode] : aceModes);
  const themes = Array.isArray(theme) ? theme : (theme ? [theme] : aceThemes);
  if (!aceModule) {
    aceModule$ = from(import('ace')).pipe(
      tap((module) => {
        aceModule = module;
      })
    );
  }
  return aceModule$.pipe(
    mergeMap(() => loadAceDependencies(modes, themes)),
    map(() => aceModule)
  );
}

export class Range implements Ace.Range {

  public start: Ace.Point;
  public end: Ace.Point;

  constructor(startRow: number, startColumn: number, endRow: number, endColumn: number) {
    this.start = {
      row: startRow,
      column: startColumn
    };

    this.end = {
      row: endRow,
      column: endColumn
    };
  }

  static fromPoints(start: Ace.Point, end: Ace.Point): Ace.Range {
    return new Range(start.row, start.column, end.row, end.column);
  }

  clipRows(firstRow: number, lastRow: number): Ace.Range {
    let end: Ace.Point;
    let start: Ace.Point;
    if (this.end.row > lastRow) {
      end = {row: lastRow + 1, column: 0};
    } else if (this.end.row < firstRow) {
      end = {row: firstRow, column: 0};
    }

    if (this.start.row > lastRow) {
      start = {row: lastRow + 1, column: 0};
    } else if (this.start.row < firstRow) {
      start = {row: firstRow, column: 0};
    }
    return Range.fromPoints(start || this.start, end || this.end);
  }

  clone(): Ace.Range {
    return Range.fromPoints(this.start, this.end);
  }

  collapseRows(): Ace.Range {
    if (this.end.column === 0) {
      return new Range(this.start.row, 0, Math.max(this.start.row, this.end.row - 1), 0);
    } else {
      return new Range(this.start.row, 0, this.end.row, 0);
    }
  }

  compare(row: number, column: number): number {
    if (!this.isMultiLine()) {
      if (row === this.start.row) {
        return column < this.start.column ? -1 : (column > this.end.column ? 1 : 0);
      }
    }

    if (row < this.start.row) {
      return -1;
    }

    if (row > this.end.row) {
      return 1;
    }

    if (this.start.row === row) {
      return column >= this.start.column ? 0 : -1;
    }

    if (this.end.row === row) {
      return column <= this.end.column ? 0 : 1;
    }

    return 0;
  }

  compareEnd(row: number, column: number): number {
    if (this.end.row === row && this.end.column === column) {
      return 1;
    } else {
      return this.compare(row, column);
    }
  }

  compareInside(row: number, column: number): number {
    if (this.end.row === row && this.end.column === column) {
      return 1;
    } else if (this.start.row === row && this.start.column === column) {
      return -1;
    } else {
      return this.compare(row, column);
    }
  }

  comparePoint(p: Ace.Point): number {
    return this.compare(p.row, p.column);
  }

  compareRange(range: Ace.Range): number {
    let cmp: number;
    const end = range.end;
    const start = range.start;

    cmp = this.compare(end.row, end.column);
    if (cmp === 1) {
      cmp = this.compare(start.row, start.column);
      if (cmp === 1) {
        return 2;
      } else if (cmp === 0) {
        return 1;
      } else {
        return 0;
      }
    } else if (cmp === -1) {
      return -2;
    } else {
      cmp = this.compare(start.row, start.column);
      if (cmp === -1) {
        return -1;
      } else if (cmp === 1) {
        return 42;
      } else {
        return 0;
      }
    }
  }

  compareStart(row: number, column: number): number {
    if (this.start.row === row && this.start.column === column) {
      return -1;
    } else {
      return this.compare(row, column);
    }
  }

  contains(row: number, column: number): boolean {
    return this.compare(row, column) === 0;
  }

  containsRange(range: Ace.Range): boolean {
    return this.comparePoint(range.start) === 0 && this.comparePoint(range.end) === 0;
  }

  extend(row: number, column: number): Ace.Range {
    const cmp = this.compare(row, column);
    let end: Ace.Point;
    let start: Ace.Point;
    if (cmp === 0) {
      return this;
    } else if (cmp === -1) {
      start = {row, column};
    } else {
      end = {row, column};
    }
    return Range.fromPoints(start || this.start, end || this.end);
  }

  inside(row: number, column: number): boolean {
    if (this.compare(row, column) === 0) {
      return !(this.isEnd(row, column) || this.isStart(row, column));
    }
    return false;
  }

  insideEnd(row: number, column: number): boolean {
    if (this.compare(row, column) === 0) {
      return !this.isStart(row, column);
    }
    return false;
  }

  insideStart(row: number, column: number): boolean {
    if (this.compare(row, column) === 0) {
      return !this.isEnd(row, column);
    }
    return false;
  }

  intersects(range: Ace.Range): boolean {
    const cmp = this.compareRange(range);
    return (cmp === -1 || cmp === 0 || cmp === 1);
  }

  isEmpty(): boolean {
    return (this.start.row === this.end.row && this.start.column === this.end.column);
  }

  isEnd(row: number, column: number): boolean {
    return this.end.row === row && this.end.column === column;
  }

  isEqual(range: Ace.Range): boolean {
    return this.start.row === range.start.row &&
      this.end.row === range.end.row &&
      this.start.column === range.start.column &&
      this.end.column === range.end.column;
  }

  isMultiLine(): boolean {
    return (this.start.row !== this.end.row);
  }

  isStart(row: number, column: number): boolean {
    return this.start.row === row && this.start.column === column;
  }

  moveBy(row: number, column: number): void {
    this.start.row += row;
    this.start.column += column;
    this.end.row += row;
    this.end.column += column;
  }

  setEnd(row: number, column: number): void {
    if (typeof row === 'object') {
      this.end.column = (row as Ace.Point).column;
      this.end.row = (row as Ace.Point).row;
    } else {
      this.end.row = row;
      this.end.column = column;
    }
  }

  setStart(row: number, column: number): void {
    if (typeof row === 'object') {
      this.start.column = (row as Ace.Point).column;
      this.start.row = (row as Ace.Point).row;
    } else {
      this.start.row = row;
      this.start.column = column;
    }
  }

  toScreenRange(session: Ace.EditSession): Ace.Range {
    const screenPosStart = session.documentToScreenPosition(this.start);
    const screenPosEnd = session.documentToScreenPosition(this.end);

    return new Range(
      screenPosStart.row, screenPosStart.column,
      screenPosEnd.row, screenPosEnd.column
    );
  }

}
