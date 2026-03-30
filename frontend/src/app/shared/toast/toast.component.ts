import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { ToastService, Toast } from '../../services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- Standard Notification Top Right -->
    <div class="tf-toast-container-standard" *ngIf="getStandardToasts().length > 0">
      <div *ngFor="let toast of getStandardToasts(); trackBy: trackById"
           class="tf-toast"
           [class.tf-toast-success]="toast.type === 'success'"
           [class.tf-toast-error]="toast.type === 'error'"
           [class.tf-toast-info]="toast.type === 'info'"
           [class.tf-toast-warning]="toast.type === 'warning'"
           [class.tf-toast-removing]="toast.removing"
           (click)="dismiss(toast.id)">
        <i class="bi" [ngClass]="toast.icon"></i>
        <span class="tf-toast-msg">{{ toast.message }}</span>
        <button class="tf-toast-close" (click)="dismiss(toast.id); $event.stopPropagation()">
          <i class="bi bi-x"></i>
        </button>
      </div>
    </div>

    <!-- Confirmation Modal-style (Center with Blur Backdrop) -->
    <div class="tf-confirm-overlay" *ngIf="getConfirmToasts().length > 0">
      <div class="tf-confirm-backdrop" (click)="getConfirmToasts()[0].onCancel?.()"></div>
      <div *ngFor="let toast of getConfirmToasts(); trackBy: trackById"
           class="tf-toast tf-toast-confirm"
           [class.tf-toast-removing]="toast.removing">
        <i class="bi" [ngClass]="toast.icon"></i>
        <div class="flex-grow-1">
          <div class="tf-toast-msg fw-bold h6 mb-2">{{ toast.message }}</div>
          <div class="tf-toast-actions d-flex gap-2">
            <button class="btn btn-light btn-sm py-1 px-4 fw-bold text-dark" (click)="toast.onConfirm?.(); $event.stopPropagation()">Confirm</button>
            <button class="btn btn-outline-light btn-sm py-1 px-4" (click)="toast.onCancel?.(); $event.stopPropagation()">Cancel</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .tf-toast-container-standard {
      position: fixed;
      top: 80px;
      right: 20px;
      z-index: 10000;
      display: flex;
      flex-direction: column;
      gap: 10px;
      max-width: 400px;
      pointer-events: none;
    }
    .tf-confirm-overlay {
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10100;
    }
    .tf-confirm-backdrop {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.4);
      backdrop-filter: blur(8px);
      animation: fadeIn 0.3s ease-out;
    }
    .tf-toast {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 16px 24px;
      border-radius: 16px;
      font-size: 0.95rem;
      font-weight: 500;
      box-shadow: 0 15px 50px rgba(0,0,0,0.15);
      backdrop-filter: blur(24px);
      pointer-events: all;
      cursor: pointer;
      animation: tf-toast-in-right 0.35s cubic-bezier(0.21, 1.02, 0.73, 1) forwards;
      border: 1px solid var(--tf-border);
      background: var(--tf-surface);
      color: var(--tf-text);
    }
    
    [data-theme='dark'] .tf-toast {
      background: rgba(30, 41, 59, 0.95);
      border-color: rgba(255,255,255,0.1);
      box-shadow: 0 15px 50px rgba(0,0,0,0.4);
    }

    .tf-toast-confirm {
      position: relative;
      z-index: 10110;
      min-width: 400px;
      padding: 32px;
      flex-direction: row;
      background: var(--tf-surface);
      border: 1px solid var(--tf-border);
      animation: tf-toast-in-center 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
      box-shadow: 0 30px 60px rgba(0,0,0,0.2);
    }
    
    [data-theme='dark'] .tf-toast-confirm {
      background: rgba(15, 23, 42, 0.98);
      box-shadow: 0 30px 60px rgba(0,0,0,0.6);
    }

    .tf-toast-removing {
      animation: tf-toast-out-right 0.3s cubic-bezier(0.06, 0.71, 0.55, 1) forwards;
    }
    .tf-toast-confirm.tf-toast-removing {
      animation: tf-toast-out-center 0.2s ease-in forwards;
    }
    .tf-toast i:first-child {
      font-size: 1.4rem;
      flex-shrink: 0;
      filter: drop-shadow(0 0 8px currentColor);
    }
    .tf-toast-success i:first-child { color: #10B981; }
    .tf-toast-error i:first-child { color: #EF4444; }
    .tf-toast-info i:first-child { color: #3B82F6; }
    .tf-toast-warning i:first-child { color: #F59E0B; }

    .tf-toast-confirm i:first-child {
      font-size: 2.5rem;
      margin-right: 16px;
      color: #3B82F6;
      filter: drop-shadow(0 0 12px #3B82F6);
    }
    /* Glowing Side Borders */
    .tf-toast-success { border-left: 5px solid #10B981; box-shadow: 0 10px 30px rgba(16, 185, 129, 0.1); }
    .tf-toast-error { border-left: 5px solid #EF4444; box-shadow: 0 10px 30px rgba(239, 68, 68, 0.1); }
    .tf-toast-info { border-left: 5px solid #3B82F6; box-shadow: 0 10px 30px rgba(59, 130, 246, 0.1); }
    .tf-toast-warning { border-left: 5px solid #F59E0B; box-shadow: 0 10px 30px rgba(245, 158, 11, 0.1); }
    
    .tf-toast-msg {
      flex: 1;
      line-height: 1.5;
      letter-spacing: -0.01em;
    }
    .tf-toast-close {
      background: none;
      border: none;
      padding: 0;
      margin-left: 4px;
      opacity: 0.6;
      cursor: pointer;
      font-size: 1rem;
      color: inherit;
      line-height: 1;
    }
    .tf-toast-close:hover { opacity: 1; }
    .tf-toast-success {
      background: rgba(15, 23, 42, 0.95);
    }
    .tf-toast-error {
      background: rgba(15, 23, 42, 0.95);
    }
    .tf-toast-info {
      background: rgba(15, 23, 42, 0.95);
    }
    .tf-toast-warning {
      background: rgba(15, 23, 42, 0.95);
    }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
    @keyframes tf-toast-in-right {
      from { opacity: 0; transform: translateX(100%) scale(0.9); }
      to { opacity: 1; transform: translateX(0) scale(1); }
    }
    @keyframes tf-toast-out-right {
      from { opacity: 1; transform: translateX(0) scale(1); }
      to { opacity: 0; transform: translateX(100%) scale(0.9); }
    }
    @keyframes tf-toast-in-center {
      from { opacity: 0; transform: scale(0.8) translateY(20px); }
      to { opacity: 1; transform: scale(1) translateY(0); }
    }
    @keyframes tf-toast-out-center {
      from { opacity: 1; transform: scale(1); }
      to { opacity: 0; transform: scale(0.9); }
    }
  `]
})
export class ToastComponent implements OnInit, OnDestroy {
  toasts: Toast[] = [];
  private sub!: Subscription;

  constructor(private toastService: ToastService) {}

  ngOnInit(): void {
    this.sub = this.toastService.toasts$.subscribe(t => this.toasts = t);
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }

  getStandardToasts(): Toast[] {
    return this.toasts.filter(t => !t.isConfirm);
  }

  getConfirmToasts(): Toast[] {
    return this.toasts.filter(t => t.isConfirm);
  }

  trackById(_: number, toast: Toast): number {
    return toast.id;
  }
}
