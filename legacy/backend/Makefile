.PHONY: demo dev test
demo:        ## sobe com dados fictícios em http://localhost:8000
	JIRADASH_MODE=demo python -m uvicorn jiradash.main:app --reload
dev: demo
test:
	python -m pytest -q
