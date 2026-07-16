from __future__ import annotations

from typing import Protocol


class Confirmer(Protocol):
    def confirm(self, title: str, message: str) -> bool: ...


class NativeConfirmer:
    def confirm(self, title: str, message: str) -> bool:
        try:
            import tkinter as tk
            from tkinter import messagebox

            root = tk.Tk()
            root.withdraw()
            root.attributes("-topmost", True)
            accepted = bool(messagebox.askyesno(title, message, parent=root, icon="warning"))
            root.destroy()
            return accepted
        except Exception:
            return False

