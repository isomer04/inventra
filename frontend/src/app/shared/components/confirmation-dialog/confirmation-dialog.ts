import { Component, input, output, viewChild, ElementRef, afterNextRender } from '@angular/core';

@Component({
  selector: 'app-confirmation-dialog',
  standalone: true,
  imports: [],
  templateUrl: './confirmation-dialog.html',
  styleUrl: './confirmation-dialog.scss',
})
export class ConfirmationDialog {
  title        = input('Confirm');
  message      = input.required<string>();
  confirmLabel = input('Delete');
  cancelLabel  = input('Cancel');
  danger       = input(true);

  confirmed = output<void>();
  cancelled = output<void>();

  // Focus the confirm button when the dialog opens so keyboard users can
  // press Enter to confirm or Tab to reach Cancel without extra navigation.
  private readonly confirmBtn = viewChild<ElementRef<HTMLButtonElement>>('confirmBtn');

  constructor() {
    afterNextRender(() => {
      this.confirmBtn()?.nativeElement.focus();
    });
  }
}
