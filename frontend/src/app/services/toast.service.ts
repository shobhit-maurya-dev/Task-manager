import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface Toast {
  id: number;
  type: 'success' | 'error' | 'info' | 'warning' | 'confirm';
  message: string;
  icon: string;
  removing?: boolean;
  isConfirm?: boolean;
  onConfirm?: () => void;
  onCancel?: () => void;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private counter = 0;
  private toastsSubject = new BehaviorSubject<Toast[]>([]);
  toasts$ = this.toastsSubject.asObservable();

  success(message: string): void {
    this.addToast('success', message, 'bi-check-circle-fill');
  }

  error(message: string): void {
    this.addToast('error', message, 'bi-exclamation-triangle-fill');
  }

  info(message: string): void {
    this.addToast('info', message, 'bi-info-circle-fill');
  }

  warning(message: string): void {
    this.addToast('warning', message, 'bi-exclamation-circle-fill');
  }

  confirm(message: string, onConfirm: () => void, onCancel?: () => void): void {
    const id = ++this.counter;
    const toast: Toast = { 
      id, 
      type: 'confirm', 
      message, 
      icon: 'bi-question-circle-fill',
      isConfirm: true,
      onConfirm: () => {
        onConfirm();
        this.dismiss(id);
      },
      onCancel: () => {
        if (onCancel) onCancel();
        this.dismiss(id);
      }
    };
    const current = this.toastsSubject.value;
    this.toastsSubject.next([...current, toast]);
    // Confirmation toasts don't auto-dismiss
  }

  private addToast(type: Toast['type'], message: string, icon: string): void {
    const id = ++this.counter;
    const toast: Toast = { id, type, message, icon };
    const current = this.toastsSubject.value;
    this.toastsSubject.next([...current, toast]);

    setTimeout(() => this.dismiss(id), 4000);
  }

  dismiss(id: number): void {
    const current = this.toastsSubject.value;
    this.toastsSubject.next(
      current.map(t => t.id === id ? { ...t, removing: true } : t)
    );
    setTimeout(() => {
      this.toastsSubject.next(
        this.toastsSubject.value.filter(t => t.id !== id)
      );
    }, 300);
  }
}
