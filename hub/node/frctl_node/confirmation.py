from __future__ import annotations

from typing import Protocol


class Confirmer(Protocol):
    def confirm(self, title: str, message: str) -> bool: ...


class NativeConfirmer:
    def __init__(self) -> None:
        self.available, self.failure_reason = self._probe()

    @staticmethod
    def _probe() -> tuple[bool, str | None]:
        try:
            import tkinter as tk

            root = tk.Tk()
            root.withdraw()
            root.update_idletasks()
            root.destroy()
            return True, None
        except Exception as exc:
            return False, type(exc).__name__

    def confirm(self, title: str, message: str) -> bool:
        if not self.available:
            return False
        try:
            import tkinter as tk
            from tkinter import messagebox

            root = tk.Tk()
            root.withdraw()
            root.attributes("-topmost", True)
            accepted = bool(messagebox.askyesno(title, message, parent=root, icon="warning"))
            root.destroy()
            return accepted
        except Exception as exc:
            self.available = False
            self.failure_reason = type(exc).__name__
            return False

