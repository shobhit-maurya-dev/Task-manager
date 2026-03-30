import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'relativeTime',
  standalone: true,
  pure: false
})
export class RelativeTimePipe implements PipeTransform {
  transform(date?: string | Date | null): string {
    if (!date) return '';

    const time = this.parseToUtcTimestamp(date);
    const now = Date.now();
    let diff = now - time;
    if (diff < 0) diff = 0;

    return this.format(diff, time);
  }

  private parseToUtcTimestamp(value: string | Date): number {
    if (value instanceof Date) {
      return value.getTime();
    }

    // Try parsing the value natively. 
    // Modern browsers usually parse ISO strings without "Z" as local time.
    let timestamp = Date.parse(value);
    
    // Fallback: If parsing fails, try substituting space with 'T' (for SQL timestamps like "2026-03-29 18:08")
    if (isNaN(timestamp)) {
      timestamp = Date.parse(value.replace(' ', 'T'));
    }

    return timestamp;
  }

  private format(diff: number, originalDate: number): string {
    const date = new Date(originalDate);
    const now = new Date();

    const seconds = Math.floor(diff / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    const isToday = date.toDateString() === now.toDateString();

    const yesterday = new Date();
    yesterday.setDate(now.getDate() - 1);
    const isYesterday = date.toDateString() === yesterday.toDateString();

    const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true });

    if (seconds < 60) {
      return 'Just now';
    }

    if (minutes < 60) {
      if (isToday) return `${minutes}m ago (${timeStr})`;
      return `${minutes} minute${minutes !== 1 ? 's' : ''} ago`;
    }

    if (isToday) {
      return `Today at ${timeStr}`;
    }

    if (isYesterday) {
      return `Yesterday at ${timeStr}`;
    }

    if (hours < 24) {
      return `${hours} hour${hours !== 1 ? 's' : ''} ago`;
    }

    const days = Math.floor(hours / 24);
    if (days < 7) {
      return `${days} day${days !== 1 ? 's' : ''} ago`;
    }

    return date.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' }) + ' ' + timeStr;
  }
}