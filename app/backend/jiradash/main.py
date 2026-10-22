from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .api.routes import router
from .config import settings
from .db import Base, SessionLocal, engine
from .jobs import start_scheduler
from .models import Issue
from . import teams

logging.basicConfig(level=logging.INFO)
FRONT = Path(__file__).resolve().parents[2] / "frontend"


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(engine)
    with SessionLocal() as db:
        teams.bootstrap(db, online_holidays=settings.mode != "demo")
        if settings.mode == "demo" and db.query(Issue).count() == 0:
            from .demo import seed_demo
            seed_demo(db)
    start_scheduler(SessionLocal)
    yield


app = FastAPI(title="Dashboard Jira por Squad", version="1.0.0", lifespan=lifespan)
app.include_router(router)
app.mount("/static", StaticFiles(directory=FRONT), name="static")


@app.get("/", include_in_schema=False)
def index():
    return FileResponse(FRONT / "index.html")
